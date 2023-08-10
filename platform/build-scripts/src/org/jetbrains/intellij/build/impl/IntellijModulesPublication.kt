// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * Publishes specified nightly versions of Intellij modules as a Maven artifacts using the output of [org.jetbrains.intellij.build.impl.MavenArtifactsBuilder].
 *
 * <p>
 * Note: Requires installed `mvn`.
 * </p>
 */
class IntellijModulesPublication(
  private val context: CompilationContext,
  private val options: Options,
) : AutoCloseable {
  private val mavenSettings: File = mavenSettings()

  override fun close() {
    mavenSettings.delete()
  }

  constructor(context: CompilationContext) : this(context = context, options = Options(version = context.options.buildNumber!!))

  class Options(
    val version: String,
    var modulesToPublish: List<String> = listProperty("intellij.modules.publication.list"),
    /**
     * Output of [org.jetbrains.intellij.build.impl.MavenArtifactsBuilder]
     */
    var outputDir: Path = property("intellij.modules.publication.prebuilt.artifacts.dir")!!.let { Path.of(it).normalize() },
  ) {
    companion object {
      private fun property(property: String) = System.getProperty(property)

      private fun listProperty(propertyName: String, defaultList: List<String> = listOf()): List<String> {
        return property(propertyName)?.splitToSequence(',')?.map { it.trim() }?.filter { !it.isEmpty() }?.toList() ?: defaultList
      }
    }

    var uploadRetryCount = 3
    var repositoryUser: String? = property("intellij.modules.publication.repository.user")
    var repositoryPassword: String? = property("intellij.modules.publication.repository.password")

    /**
     * URL where the artifacts will be deployed
     *
     *  <p>
     *  Note: Append /;publish=1;override=1 for Bintray
     *  </p>
     */
    var repositoryUrl: String? = property("intellij.modules.publication.repository.url")

    /**
     * Base for url to check if an artifact was already published.
     * Check [artifactExists] for the details.
     */
    var checkArtifactExistsUrl = property("intellij.modules.publication.repository.existsUrl").trimEnd('/')

    var modulesToExclude = listProperty("intellij.modules.publication.excluded", listOf("fleet", "multiplatform-tests"))
  }

  fun publish() {
    var modules = LinkedHashSet<JpsModule>()
    if (options.modulesToPublish == listOf("*")) {
      modules.addAll(context.project.modules)
    }
    else {
      options.modulesToPublish.forEach {
        val module = context.findRequiredModule(it)
        modules.add(module)
        transitiveModuleDependencies(module, modules)
      }
    }
    modules = modules.filterTo(LinkedHashSet()) { !options.modulesToExclude.contains(it.name) }
    if (modules.isEmpty()) {
      context.messages.warning("Nothing to publish")
    }
    for (module in modules) {
      val coordinates = MavenArtifactsBuilder.generateMavenCoordinates(module.name, options.version)
      deployModuleArtifact(coordinates)

      val squashedCoordinates = MavenArtifactsBuilder.generateMavenCoordinatesSquashed(module.name, options.version)
      if (Files.exists(options.outputDir.resolve(squashedCoordinates.directoryPath))) {
        deployModuleArtifact(squashedCoordinates)
      }
    }
  }

  private fun deployModuleArtifact(coordinates: MavenCoordinates) {
    val dir = options.outputDir.resolve(coordinates.directoryPath).toFile()
    val pom = File(dir, coordinates.getFileName("", "pom"))
    val jar = File(dir, coordinates.getFileName("", "jar"))
    val sources = File(dir, coordinates.getFileName("sources", "jar"))
    if (jar.exists()) {
      deployJar(jar, pom, coordinates)
    }
    else {
      context.messages.warning("$coordinates $jar is not found")
    }
    if (sources.exists()) {
      deploySources(sources, coordinates)
    }
    else {
      context.messages.warning("$coordinates $sources is not found")
    }
  }

  private fun transitiveModuleDependencies(module: JpsModule, result: MutableCollection<JpsModule>) {
    MavenArtifactsBuilder.scopedDependencies(module)
      .filter { it.key is JpsModuleDependency }
      .forEach {
        val dependencyModule = (it.key as JpsModuleDependency).module!!
        if (!result.contains(dependencyModule)) {
          result.add(dependencyModule)
          transitiveModuleDependencies(dependencyModule, result)
        }
      }
  }

  private fun deployJar(jar: File, pom: File, coordinates: MavenCoordinates) {
    deployFile(jar, coordinates, "", "-DpomFile=${pom.absolutePath}")
  }

  private fun deploySources(sources: File, coordinates: MavenCoordinates) {
    deployFile(sources, coordinates, "sources",
               "-DgroupId=${coordinates.groupId}",
               "-DartifactId=${coordinates.artifactId}",
               "-Dversion=${coordinates.version}",
               "-Dpackaging=java-source",
               "-DgeneratePom=false"
    )
  }

  private fun deployFile(file: File, coordinates: MavenCoordinates, classifier: String, vararg args: String) {
    if (artifactExists(coordinates, classifier, file.name.split('.').last())) {
      context.messages.info("Artifact $coordinates was already published.")
    }
    else {
      context.messages.info("Upload of ${file.name}")
      val process = ProcessBuilder(
        "mvn", "--settings", mavenSettings.absolutePath,
        "deploy:deploy-file",
        "-DrepositoryId=server-id",
        "-Dfile=${file.absolutePath}",
        "-Durl=${options.repositoryUrl}",
        "-DretryFailedDeploymentCount=${options.uploadRetryCount}",
        *args)
        .inheritIO()
        .start()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        context.messages.error("Upload of ${file.name} failed with exit code $exitCode")
      }
    }
  }

  private fun artifactExists(coordinates: MavenCoordinates, classifier: String, packaging: String): Boolean {
    val url = URL("${options.checkArtifactExistsUrl}/${coordinates.directoryPath}/${coordinates.getFileName(classifier, packaging)}")

    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "HEAD"
    connection.instanceFollowRedirects = true

    val responseCode = connection.responseCode
    if (responseCode == 302) {
      // Redirect will not be performed in case of protocol change. E.g. http -> https.
      context.messages.warning("Redirect code was returned, but no redirect was performed. Please check url $url")
    }
    return responseCode == 200
  }

  private fun mavenSettings(): File {
    var server = ""
    val password = options.repositoryPassword
    val user = options.repositoryUser
    if (password != null && user != null && !password.isEmpty() && !user.isEmpty()) {
      server = """<server>
        <id>server-id</id>
        <username>$user</username>
        <password>$password</password>
      </server>"""
    }

    val file = FileUtil.createTempFile("settings", ".xml")
    Files.writeString(file.toPath(), """
      <settings xmlns="https://maven.apache.org/SETTINGS/1.0.0"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://maven.apache.org/SETTINGS/1.0.0
                    https://maven.apache.org/xsd/settings-1.0.0.xsd">
        <servers>
           $server
        </servers>
       </settings>
        """.trimIndent())
    return file
  }
}
