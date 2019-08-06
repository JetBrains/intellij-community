// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency

/**
 * Publishes specified nightly versions of Intellij modules as a Maven artifacts using the output of {@link org.jetbrains.intellij.build.impl.MavenArtifactsBuilder}.
 *
 * <p>
 * Note: Requires installed `mvn`.
 * </p>
 */
@CompileStatic
@SuppressWarnings("unused")
class IntellijModulesPreview {
  private final BuildContext buildContext
  private final int uploadRetryCount = 3
  private final String repositoryId
  private final String repositoryUrl
  private final File mavenSettings

  IntellijModulesPreview(BuildContext context) {
    buildContext = context
    repositoryId = require('repository.id')
    repositoryUrl = "${require('repository.publicationUrl')}/;publish=1;"
    mavenSettings = settingsXml()
  }

  private String require(String property) {
    System.getProperty(property) ?: buildContext.messages.error("$property is not specifed")
  }

  /**
   * @param modulesOutputDir output of {@link org.jetbrains.intellij.build.impl.MavenArtifactsBuilder}
   */
  void publish(List<String> modulesToPublish, File modulesOutputDir) {
    def modules = new HashSet<JpsModule>()
    modulesToPublish.each {
      def module = buildContext.findRequiredModule(it)
      modules << module
      transitiveModuleDependencies(module, modules)
    }
    modules.each {
      def coordinates = MavenArtifactsBuilder.generateMavenCoordinates(
        it.name, buildContext.messages, buildContext.buildNumber
      )
      def dir = new File(modulesOutputDir, coordinates.directoryPath)
      def pom = new File(dir, coordinates.getFileName('', 'pom'))
      def jar = new File(dir, coordinates.getFileName('', 'jar'))
      def sources = new File(dir, coordinates.getFileName('sources', 'jar'))
      deployJar(jar, pom)
      if (sources.exists()) {
        deploySources(sources, coordinates)
      }
    }
  }

  private def transitiveModuleDependencies(JpsModule module, Collection<JpsModule> result) {
    MavenArtifactsBuilder.scopedDependencies(module)
      .findAll { it.key instanceof JpsModuleDependency }
      .each {
        def dependencyModule = (it.key as JpsModuleDependency).module
        if (!result.contains(dependencyModule)) {
          result << dependencyModule
          transitiveModuleDependencies(dependencyModule, result)
        }
      }
  }

  private def deployJar(File jar, File pom) {
    deployFile(jar, ["-DpomFile=$pom.absolutePath"])
  }

  private def deploySources(File sources, MavenArtifactsBuilder.MavenCoordinates coordinates) {
    deployFile(sources, [
      "-DgroupId=$coordinates.groupId",
      "-DartifactId=$coordinates.artifactId",
      "-Dversion=$coordinates.version",
      '-Dpackaging=java-source',
      '-DgeneratePom=false'
    ])
  }

  private def deployFile(File file, Collection args) {
    buildContext.messages.info("Upload of $file.name")
    def process = ([
                     'mvn', '--settings', mavenSettings.absolutePath,
                     'deploy:deploy-file',
                     "-Dfile=$file.absolutePath",
                     "-DrepositoryId=$repositoryId",
                     "-Durl=$repositoryUrl",
                     "-DretryFailedDeploymentCount=$uploadRetryCount"
                   ] + args).execute()
    def output = process.text
    def exitCode = process.waitFor()
    if (exitCode != 0) {
      buildContext.messages.error("Upload of $file.name failed with exit code $exitCode: $output")
    }
  }

  private File settingsXml() {
    File.createTempFile('settings', '.xml').with {
      it << """<settings xmlns="https://maven.apache.org/SETTINGS/1.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="https://maven.apache.org/SETTINGS/1.0.0
                            https://maven.apache.org/xsd/settings-1.0.0.xsd">
                <servers>
                  <server>
                    <id>$repositoryId</id>
                    <username>${require('repository.user')}</username>
                    <password>${require('repository.password')}</password>
                  </server>
                </servers>
               </settings>
      """.stripIndent()
      it.deleteOnExit()
      it
    }
  }
}
