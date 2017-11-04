#!/usr/bin/env groovy
def jobDeloyList = ["dev", "test", "uat"]
def jobBuildList = ["feature", "dev", "test"]

def gitUrl = "https://github.com/springdo/vue-js-ci-app"
def appName = "vue-app"
def pipelineNamespace = "ci-cd"
newLine = System.getProperty("line.separator")
def pipelineGeneratorVersion = "${JOB_NAME}.${BUILD_ID}"

jobDeloyList.each {
    def jobNameDeploy = it + "-ds-deploy"
    def jobNameTests =  it + "-ds-e2e"
    def buildTag = it + "-ds-build.1234"
    def jobDescription = "THIS JOB WAS GENERATED BY THE JENKINS SEED JOB - ${pipelineGeneratorVersion}.  \n" + it + " job for the vue js app "
    def projectNamespace = "labs-" + it

    job(jobNameDeploy) {
        description(jobDescription)
        parameters{
          string{
            name("BUILD_TAG")
            defaultValue("my-app-build.1234")
            description("The BUILD_TAG is the \${JOB_NAME}.\${BUILD_NUMBER} of the successful build to be promoted. For example ${buildTag}")
          }
        }
        steps {
          steps {
            shell('#!/bin/bash' + newLine +
                  'PIPELINES_NAMESPACE=' + pipelineNamespace  + newLine +
                  'NAMESPACE=' + projectNamespace  + newLine +
                  'NAME=' + appName  + newLine +
                  'oc tag ${PIPELINES_NAMESPACE}/${NAME}:latest ${NAMESPACE}/${NAME}:${BUILD_TAG}' + newLine +
                  'oc project ${NAMESPACE}' + newLine +
                  'oc patch dc ${NAME} -p "spec:' + newLine +
                  '  template:' + newLine +
                  '    spec:' + newLine +
                  '      containers:' + newLine +
                  '        - name: ${NAME}' + newLine +
                  '          image: docker-registry.default.svc/${NAMESPACE}/${NAME}:${BUILD_TAG}"' + newLine +
                  'oc rollout latest dc/${NAME}')
          }
          openShiftDeploymentVerifier {
            apiURL('')
            depCfg(appName)
            namespace(projectNamespace)
            // This optional field's value represents the number expected running pods for the deployment for the DeploymentConfig specified.
            replicaCount('1')
            authToken('')
            verbose('yes')
            // This flag is the toggle for turning on or off the verification that the specified replica count for the deployment has been reached.
            verifyReplicaCount('yes')
            waitTime('')
            waitUnit('sec')
          }
        }
        publishers {
          downstreamParameterized {
              trigger(jobNameTests) {
                  condition('SUCCESS')
                  parameters {
                      currentBuild()
                  }
              }
          }
        }
    }

    job(jobNameTests) {
        description(jobDescription)
        steps {
          steps {
            shell('#!/bin/bash' + newLine +
                  'echo run e2e tests')
          }
        }
    }
}



jobBuildList.each {
  def jobName = it + "-ds-build"
  def jobPrefix = it
  def jobDescription = "THIS JOB WAS GENERATED BY THE JENKINS SEED JOB - ${pipelineGeneratorVersion}.  \n"  + it + "build job for the vue js app "

  job(jobName) {
      description(jobDescription)
  	  label('npm-build-pod')
    	scm {
        git {
          remote {
            name('origin')
            url(gitUrl)
          }
          if (jobName == 'dev-ds-build'){
            branch('develop')
          }
          else if (jobName == 'test-ds-build'){
            branch('master')
          }
          else {
            branch('origin/feature/**')
          }
        }
      }
      triggers {
        cron('H/60 H/2 * * *')
      }
      steps {
        steps {
          shell('#!/bin/bash' + newLine +
                'scl enable rh-nodejs6 \'npm install && \
                	npm run unit && \
                	npm run build\'' + newLine +
                'mkdir package-contents' + newLine +
                'mv dist Dockerfile package-contents' + newLine +
                'oc patch bc vue-app -p "spec:' + newLine +
                '   nodeSelector:' + newLine +
                '     output:' + newLine +
                '       to:' + newLine +
                '       name: vue-app:${JOB_NAME}.${BUILD_NUMBER}"' + newLine +
                'oc start-build vue-app --from-dir=package-contents/ --follow')
        }
      }
      publishers {
        archiveArtifacts('**')
      	cobertura('reports/coverage/cobertura.xml') {
          failNoReports(true)
          sourceEncoding('ASCII')
          // the following targets are added by default to check the method, line and conditional level coverage
          methodTarget(80, 0, 0)
          lineTarget(80, 0, 0)
          conditionalTarget(70, 0, 0)
        }
        publishHtml {
          report('reports/coverage/lcov-report') {
            reportName('HTML Code Coverage Report')
            allowMissing(false)
          	alwaysLinkToLastBuild(false)
          }
        }
        xUnitPublisher {
          tools {
          	jUnitType {
              pattern('reports/**/unit-report.xml')
              skipNoTestFiles(false)
              failIfNotNew(true)
              deleteOutputFiles(true)
              stopProcessingIfError(true)
            }
          }
          thresholdMode(0)
          testTimeMargin('3000')
        }

        // println "INFO - blah ${jobName}"
        if (jobName == 'dev-ds-build' || jobName == 'test-ds-build') {
          println "INFO - downstream param stuff for ${jobPrefix}"
          downstreamParameterized {
              trigger(jobPrefix + '-ds-deploy') {
                  condition('SUCCESS')
                  parameters {
                      predefinedBuildParameters{
                        properties("BUILD_TAG=\${JOB_NAME}.\${BUILD_NUMBER}")
                        textParamValueOnNewLine(true)
                      }
                  }
              }
          }
        }
      }
  }

  buildPipelineView(jobPrefix + "-pipeline") {
      filterBuildQueue()
      filterExecutors()
      title(jobPrefix + " CI Pipeline")
      displayedBuilds(5)
      selectedJob(jobName)
      alwaysAllowManualTrigger()
      refreshFrequency(60)
  }
}


def jobNameRegex = '.*vue-app.*'
buildMonitorView('vue-app-monitor') {
    description('All build jobs for the vue app')
    filterBuildQueue()
    filterExecutors()
    jobs {
        regex(jobNameRegex)
    }
}
