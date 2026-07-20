// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.KspGradleLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

data class FleetKspConfig(
  val moduleName: String,
  val languageVersion: String,
  val apiVersion: String,
  val projectBaseDir: Path,
  val workDir: Path,
  val sources: List<Path>,
  val classpath: List<Path>,
  val classesOutputDirPath: Path,
  val kotlinOutputDirPath: Path,
  val resourceOutputDirPath: Path,
  val cachesDirPath: Path,
  val sourceRootPaths: List<Path>,
  val commonSourceRootPaths: List<Path>,
  val platformSourceRootPaths: List<Path>,
) {
  companion object {
    fun fromCommandArguments(
      commandName: String,
      moduleName: String,
      languageVersion: String,
      apiVersion: String,
      sources: List<Path>,
      classpath: List<Path>,
    ): FleetKspConfig {
      val projectBaseDirPath = Path.of("").toAbsolutePath().normalize()
      val workDir = Files.createTempDirectory(projectBaseDirPath, commandName)
      val sourceRootPaths = sources.map { it.toAbsolutePath().normalize() }.map { findSourceRoot(it) }.distinct()
      val commonSourceRootPaths = sourceRootPaths.filter { it.fileName.toString().contains("Common", ignoreCase = true) }
      val platformSourceRootPaths = sourceRootPaths - commonSourceRootPaths.toSet()
      return FleetKspConfig(
        moduleName = moduleName,
        languageVersion = languageVersion,
        apiVersion = apiVersion,
        projectBaseDir = projectBaseDirPath,
        workDir = workDir,
        sources = sources.map { it.toAbsolutePath().normalize() },
        classpath = classpath.map { it.toAbsolutePath().normalize() },
        classesOutputDirPath = Files.createDirectories(workDir.resolve("classes")),
        kotlinOutputDirPath = Files.createDirectories(workDir.resolve("kotlin")),
        resourceOutputDirPath = Files.createDirectories(workDir.resolve("resources")),
        cachesDirPath = Files.createDirectories(workDir.resolve("caches")),
        sourceRootPaths = sourceRootPaths,
        commonSourceRootPaths = commonSourceRootPaths,
        platformSourceRootPaths = platformSourceRootPaths,
      )
    }
  }
}

@Suppress("IO_FILE_USAGE")
fun KSPConfig.Builder.applyCommonKSPConfig(config: FleetKspConfig) {
  moduleName = config.moduleName
  sourceRoots = config.platformSourceRootPaths.map { it.toFile() }
  commonSourceRoots = config.commonSourceRootPaths.map { it.toFile() }
  libraries = config.classpath.map { it.toFile() }
  projectBaseDir = config.projectBaseDir.toFile()
  outputBaseDir = config.workDir.toFile()
  cachesDir = config.cachesDirPath.toFile()
  classOutputDir = config.classesOutputDirPath.toFile()
  kotlinOutputDir = config.kotlinOutputDirPath.toFile()
  resourceOutputDir = config.resourceOutputDirPath.toFile()
  languageVersion = config.languageVersion
  apiVersion = config.apiVersion
}

internal fun executeKsp(config: KSPConfig, processorClasspath: List<Path>): Int {
  val loggingLevel = when (System.getProperty("ksp.logging", "warn").lowercase()) {
    "error" -> KspGradleLogger.LOGGING_LEVEL_ERROR
    "warn", "warning" -> KspGradleLogger.LOGGING_LEVEL_WARN
    "info" -> KspGradleLogger.LOGGING_LEVEL_INFO
    "debug" -> KspGradleLogger.LOGGING_LEVEL_LOGGING
    else -> KspGradleLogger.LOGGING_LEVEL_WARN
  }
  val logger = KspGradleLogger(loggingLevel)
  return URLClassLoader(processorClasspath.map { it.toUri().toURL() }.toTypedArray(),
                        object {}::class.java.classLoader).use { processorClassloader ->
    val processorProviders = ServiceLoader.load(SymbolProcessorProvider::class.java, processorClassloader).toList()
    KotlinSymbolProcessing(config, processorProviders, logger).execute()
  }.code
}

internal fun findSourceRoot(source: Path): Path {
  var current: Path? = source.parent
  while (current != null) {
    val directoryName = current.fileName?.toString()
    // `srcCommonMain`, `srcWasmJsMain`, ... plus the checked-in generated flavors (`genCommonMain`, ...).
    if (directoryName != null && (directoryName.startsWith("src") || directoryName.startsWith("gen"))) {
      return current
    }
    current = current.parent
  }
  return source.parent ?: source
}
