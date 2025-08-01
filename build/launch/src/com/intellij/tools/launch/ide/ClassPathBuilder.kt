package com.intellij.tools.launch.ide

import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.tools.launch.PathsProvider
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Logger
import kotlin.io.path.name
import kotlin.io.path.pathString

class ClassPathBuilder(private val paths: PathsProvider, private val modulesToScopes: Map<String, JpsJavaClasspathKind>) {
  private val logger = Logger.getLogger(ClassPathBuilder::class.java.name)

  companion object {
    fun createClassPathArgFile(paths: PathsProvider, classpath: List<String>, pathSeparator: String = File.pathSeparator): File {
      val logFolder = paths.logFolder
      if (!logFolder.exists()) {
        logFolder.mkdirs()
      }

      val classPathArgFile = logFolder.resolve("Launcher_${UUID.randomUUID().toString().take(4)}.classpath")
      CommandLineWrapperUtil.writeArgumentsFile(classPathArgFile,
                                                listOf("-classpath", classpath.distinct().joinToString(pathSeparator)), Charsets.UTF_8)
      return classPathArgFile
    }

    fun modulesToScopes(
      mainModule: String,
      additionalRuntimeModules: List<String> = emptyList(),
      additionalTestRuntimeModules: List<String> = emptyList(),
    ): Map<String, JpsJavaClasspathKind> =
      (listOf(mainModule) + additionalRuntimeModules).associateWith { JpsJavaClasspathKind.PRODUCTION_RUNTIME } +
      additionalTestRuntimeModules.associateWith { JpsJavaClasspathKind.TEST_RUNTIME }
  }

  private val model = JpsElementFactory.getInstance().createModel() ?: throw Exception("Couldn't create JpsModel")

  fun build(logClasspath: Boolean): File {
    val classpath = buildClasspath(logClasspath)

    return createClassPathArgFile(paths, classpath)
  }

  fun buildClasspath(logClasspath: Boolean): List<String> = buildClasspath(logClasspath) { it.toRealPath().pathString }

  fun <T : Comparable<T>> buildClasspath(logClasspath: Boolean, mapper: (Path) -> T): List<T> {
    val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)

    val m2HomePath = File(SystemProperties.getUserHome())
      .resolve(".m2")
      .resolve("repository")
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", m2HomePath.canonicalPath)

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, paths.sourcesRootFolder.toPath())

    val productionOutput = paths.outputRootFolder.resolve("production")
    if (!productionOutput.isDirectory && PathManager.getArchivedCompiledClassesMapping() == null) {
      error("Production classes output directory is missing: $productionOutput")
    }

    JpsJavaExtensionService.getInstance().getProjectExtension(model.project)!!.outputUrl =
      "file://${FileUtil.toSystemIndependentName(paths.outputRootFolder.path)}"

    val startupModules = listOf("intellij.platform.boot", "intellij.configurationScript")
      .associateWith { JpsJavaClasspathKind.PRODUCTION_RUNTIME }

    return buildClasspath(modulesToScopes + startupModules, logClasspath, mapper)
  }

  private fun <T : Comparable<T>> buildClasspath(modulesToScopes: Map<String, JpsJavaClasspathKind>, logClasspath: Boolean, mapper: (Path) -> T): List<T> {
    val classpath = LinkedHashSet<T>()
    for ((moduleName, jpsJavaClasspathKind) in modulesToScopes) {
      val module = model.project.findModuleByName(moduleName)
                   ?: throw Exception("Module $moduleName not found")

      classpath.addAll(getClasspathForModule(module, jpsJavaClasspathKind, mapper))
    }

    // Uncomment for big debug output
    // seeing as client classpath gets logged anyway, there's no need to comment this out
    if (logClasspath) {
      logger.info("Created classpath:")
      for (path in classpath.distinct().sorted()) {
        logger.info("  $path")
      }
      logger.info("-- END")
    }
    else {
      logger.warning("Verbose classpath logging is disabled, set logClasspath to true to see it.")
    }

    return classpath.toList()
  }

  private fun <T> getClasspathForModule(module: JpsModule, jpsJavaClasspathKind: JpsJavaClasspathKind, mapper: (Path) -> T): List<T> {
    return JpsJavaExtensionService
      .dependencies(module)
      .recursively()
      .includedIn(jpsJavaClasspathKind)
      .classes().paths.replaceWithArchivedIfNeeded().filter { Files.exists(it) }.map { mapper(it) }.toList()
  }

  private fun Collection<Path>.replaceWithArchivedIfNeeded(): Collection<Path> {
    val mapping = PathManager.getArchivedCompiledClassesMapping() ?: return this
    return map { path ->
      if (Files.isRegularFile(path)) path
      // path is absolute, mapping contains only the last two path elements
      else mapping[path.parent.name + "/" + path.name]?.let { Path.of(it) } ?: path
    }
  }
}