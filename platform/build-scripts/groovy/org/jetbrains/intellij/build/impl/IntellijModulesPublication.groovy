// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
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
class IntellijModulesPublication {
  private final CompilationContext context
  private final File mavenSettings
  private final Options options

  IntellijModulesPublication(CompilationContext context, Options options) {
    this.context = context
    this.options = options
    this.mavenSettings = mavenSettings()
  }

  IntellijModulesPublication(CompilationContext context) {
    this(context, new Options(version: context.options.buildNumber))
  }

  static class Options {
    String version
    int uploadRetryCount = 3
    String repositoryUser = property('intellij.modules.publication.repository.user')
    String repositoryPassword = property('intellij.modules.publication.repository.password')
    /**
     * URL where the artifacts will be deployed
     *
     *  <p>
     *  Note: Append /;publish=1; for Bintray
     *  </p>
     */
    String repositoryUrl = property('intellij.modules.publication.repository.url')
    /**
     * Output of {@link org.jetbrains.intellij.build.impl.MavenArtifactsBuilder}
     */
    File outputDir = property('intellij.modules.publication.prebuilt.artifacts.dir')?.with { new File(it) }
    Collection<String> modulesToPublish = property('intellij.modules.publication.list')
                                            ?.split(',')?.toList()
                                            ?.collect { it.trim() }
                                            ?.findAll { !it.isEmpty() } ?: []

    private static String property(String property) {
      System.getProperty(property)
    }
  }

  void publish() {
    def modules = new HashSet<JpsModule>()
    if (options.modulesToPublish == ['*']) {
      modules += context.project.modules
    }
    else {
      options.modulesToPublish.each {
        def module = context.findRequiredModule(it)
        modules << module
        transitiveModuleDependencies(module, modules)
      }
    }
    if (modules.isEmpty()) context.messages.warning('Nothing to publish')
    modules.each {
      def coordinates = MavenArtifactsBuilder.generateMavenCoordinates(it.name, context.messages, options.version)
      def dir = new File(options.outputDir, coordinates.directoryPath)
      def pom = new File(dir, coordinates.getFileName('', 'pom'))
      def jar = new File(dir, coordinates.getFileName('', 'jar'))
      def sources = new File(dir, coordinates.getFileName('sources', 'jar'))
      if (jar.exists()) {
        deployJar(jar, pom)
      }
      else {
        context.messages.warning("$it.name jar is not found")
      }
      if (sources.exists()) {
        deploySources(sources, coordinates)
      }
      else {
        context.messages.warning("$it.name sources is not found")
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
    context.messages.info("Upload of $file.name")
    def process = ([
                     'mvn', '--settings', mavenSettings.absolutePath,
                     'deploy:deploy-file',
                     '-DrepositoryId=server-id',
                     "-Dfile=$file.absolutePath",
                     "-Durl=$options.repositoryUrl",
                     "-DretryFailedDeploymentCount=$options.uploadRetryCount"
                   ] + args).execute()
    def output = process.text
    def exitCode = process.waitFor()
    if (exitCode != 0) {
      context.messages.warning("Upload of $file.name failed with exit code $exitCode: $output")
    }
  }

  private File mavenSettings() {
    def server = ''
    if (options.repositoryPassword != null && options.repositoryUser != null &&
        !options.repositoryPassword.isEmpty() && !options.repositoryUser.isEmpty()) {
      server = """<server>
        <id>server-id</id>
        <username>${options.repositoryUser}</username>
        <password>${options.repositoryPassword}</password>
      </server>"""
    }
    File.createTempFile('settings', '.xml').with {
      it << """<settings xmlns="https://maven.apache.org/SETTINGS/1.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="https://maven.apache.org/SETTINGS/1.0.0
                            https://maven.apache.org/xsd/settings-1.0.0.xsd">
                <servers>
                   $server
                </servers>
               </settings>
      """.stripIndent()
      it.deleteOnExit()
      it
    }
  }
}
