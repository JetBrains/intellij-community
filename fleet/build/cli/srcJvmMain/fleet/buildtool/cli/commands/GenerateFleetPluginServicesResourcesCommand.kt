// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.multiple as multipleOptions
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KspGradleLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.ServiceLoader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.walk

private const val PLUGIN_SERVICE_FQN = "fleet.kernel.plugins.Plugin"
private val PLUGIN_SERVICE_RESOURCE = Path.of("META-INF", "services", PLUGIN_SERVICE_FQN)
private val GENERATED_RESOURCE_PATHS = listOf(
  Path.of("entityTypes.txt"),
  PLUGIN_SERVICE_RESOURCE,
)

private val REPRODUCIBLE_JAR_ENTRY_TIME: LocalDateTime = LocalDateTime.of(1980, 1, 1, 0, 0) // MS-DOS and FAT32 compatible timestamp


class GenerateFleetPluginServicesResourcesCommand : CliktCommand(
  name = "generate-fleet-plugin-services-resources",
) {
  private val sources by option("--sources").path().multipleOptions()
  private val classpath by option("--classpath").path().multipleOptions()
  private val processorClasspath by option("--processor-classpath").path().multipleOptions(required = true)
  private val moduleName by option("--module-name").required()
  private val jvmTarget by option("--jvm-target").required()
  private val languageVersion by option("--language-version").required()
  private val apiVersion by option("--api-version").required()
  private val outputDir by option("--output-dir").path().required()
  private val outputJar by option("--output-jar").path().required()

  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  @OptIn(ExperimentalPathApi::class)
  override fun run() {
    val projectBaseDirPath = Path.of("").toAbsolutePath().normalize()
    val workDir = Files.createTempDirectory(projectBaseDirPath, "fleet-plugin-services-resources-generator")
    try {
      val normalizedSources = sources.map { it.toAbsolutePath().normalize() }
      val normalizedClasspath = classpath.map { it.toAbsolutePath().normalize() }
      val normalizedProcessorClasspath = processorClasspath.map { it.toAbsolutePath().normalize() }
      val classesOutputDirPath = Files.createDirectories(workDir.resolve("classes"))
      val javaOutputDirPath = Files.createDirectories(workDir.resolve("java"))
      val kotlinOutputDirPath = Files.createDirectories(workDir.resolve("kotlin"))
      val resourceOutputDirPath = Files.createDirectories(workDir.resolve("resources"))
      val cachesDirPath = Files.createDirectories(workDir.resolve("caches"))
      val sourceRootPaths = normalizedSources.map { findSourceRoot(it) }.distinct()
      val commonSourceRootPaths = sourceRootPaths.filter { it.fileName.toString().contains("Common", ignoreCase = true) }
      val jvmSourceRootPaths = sourceRootPaths - commonSourceRootPaths.toSet()
      val javaSourceRootPaths = jvmSourceRootPaths.filter { root ->
        normalizedSources.any { it.startsWith(root) && it.toString().endsWith(".java") }
      }
      @Suppress("IO_FILE_USAGE") val kspConfig = KSPJvmConfig.Builder().apply {
        moduleName = this@GenerateFleetPluginServicesResourcesCommand.moduleName
        javaSourceRoots = javaSourceRootPaths.map { it.toFile() }
        javaOutputDir = javaOutputDirPath.toFile()
        jvmTarget = this@GenerateFleetPluginServicesResourcesCommand.jvmTarget
        sourceRoots = jvmSourceRootPaths.map { it.toFile() }
        commonSourceRoots = commonSourceRootPaths.map { it.toFile() }
        libraries = normalizedClasspath.map { it.toFile() }
        projectBaseDir = projectBaseDirPath.toFile()
        outputBaseDir = workDir.toFile()
        cachesDir = cachesDirPath.toFile()
        classOutputDir = classesOutputDirPath.toFile()
        kotlinOutputDir = kotlinOutputDirPath.toFile()
        resourceOutputDir = resourceOutputDirPath.toFile()
        languageVersion = this@GenerateFleetPluginServicesResourcesCommand.languageVersion
        apiVersion = this@GenerateFleetPluginServicesResourcesCommand.apiVersion
      }.build()

      val kspExitCode = executeKsp(kspConfig, normalizedProcessorClasspath)
      check(kspExitCode == 0) {
        "KSP failed with exit code $kspExitCode"
      }
      copyGeneratedResources(resourceOutputDirPath, outputDir)
      createResourcesJar(outputDir, outputJar)
    }
    finally {
      workDir.deleteRecursively()
    }
  }

  private fun copyGeneratedResources(resourceOutputDirPath: Path, outputDir: Path) {
    outputDir.createDirectories()
    for (relativePath in GENERATED_RESOURCE_PATHS) {
      val source = resourceOutputDirPath.resolve(relativePath)
      // TODO: Replace with outputDir.resolve(relativePath) once the bazel output directory cache error is fixed
      //  (https://jetbrains.slack.com/archives/C07NAUFL875/p1775121742002489)
      val target = outputDir.resolve(relativePath.fileName)
      logResourceState("Preparing generated resource", relativePath, source, target)
      if (!source.exists()) {
        logger.info("Skipping missing generated resource: ${source.toDebugString()}")
        continue
      }
      require(source.isRegularFile()) { "Generated resource source is not a regular file: ${source.toDebugString()}" }
      target.parent?.createDirectories()
      try {
        source.copyTo(target, overwrite = true)
      }
      catch (t: Throwable) {
        throw IllegalStateException(
          buildString {
            appendLine("Failed to copy generated resource.")
            appendLine("relativePath=$relativePath")
            appendLine("source=${source.toDebugString()}")
            appendLine("target=${target.toDebugString()}")
            appendLine("targetParent=${target.parent?.toDebugString() ?: "<null>"}")
          },
          t,
        )
      }
      require(target.isRegularFile()) { "Generated resource target was not created as a regular file: ${target.toDebugString()}" }
      logResourceState("Copied generated resource", relativePath, source, target)
    }
  }

  private fun executeKsp(config: KSPConfig, processorClasspath: List<Path>): Int {
    val loggingLevel = when (System.getProperty("ksp.logging", "warn").lowercase()) {
      "error" -> KspGradleLogger.LOGGING_LEVEL_ERROR
      "warn", "warning" -> KspGradleLogger.LOGGING_LEVEL_WARN
      "info" -> KspGradleLogger.LOGGING_LEVEL_INFO
      "debug" -> KspGradleLogger.LOGGING_LEVEL_LOGGING
      else -> KspGradleLogger.LOGGING_LEVEL_WARN
    }
    val logger = KspGradleLogger(loggingLevel)
    return URLClassLoader(processorClasspath.map { it.toUri().toURL() }.toTypedArray(), this::class.java.classLoader)
      .use { processorClassloader ->
        val processorProviders = ServiceLoader.load(SymbolProcessorProvider::class.java, processorClassloader).toList()
        KotlinSymbolProcessing(config, processorProviders, logger).execute()
      }.code
  }

  private fun findSourceRoot(source: Path): Path {
    var current: Path? = source.parent
    while (current != null) {
      if (current.fileName?.toString()?.startsWith("src") == true) {
        return current
      }
      current = current.parent
    }
    return source.parent ?: source
  }

  private fun logResourceState(message: String, relativePath: Path, source: Path, target: Path) {
    logger.info(
      "$message: relativePath=$relativePath, source=${source.toDebugString()}, target=${target.toDebugString()}"
    )
  }

  private fun createResourcesJar(outputDir: Path, outputJar: Path) {
    outputJar.parent?.createDirectories()
    outputJar.createFile()
    JarOutputStream(outputJar.outputStream()).use { jarOut ->
      outputDir.walk()
        .filter { it.isRegularFile() }
        .map { sourceFile ->
          val relativePath = outputDir.relativize(sourceFile).invariantSeparatorsPathString
          relativePath to sourceFile
        }
        .sortedBy { (relativePath, _) -> relativePath }
        .forEach { (relativePath, sourceFile) ->
          val jarEntry = JarEntry(relativePath).apply {
            timeLocal = REPRODUCIBLE_JAR_ENTRY_TIME
          }
          jarOut.putNextEntry(jarEntry)
          sourceFile.inputStream().use { it.copyTo(jarOut) }
          jarOut.closeEntry()
        }
    }
  }

  private fun Path.toDebugString(): String {
    return "${toAbsolutePath()} [exists=${exists()}, regularFile=${isRegularFile()}, directory=${isDirectory()}]"
  }
}
