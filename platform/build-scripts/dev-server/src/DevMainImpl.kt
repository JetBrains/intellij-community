// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevMainImpl")
@file:Suppress("IO_FILE_USAGE", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.telemetry.withTracer
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

data class BuildDevInfo(
  val mainClassName: String,
  val classPath: Collection<Path>,
  val systemProperties: Map<String, String>,
)

/**
 * Returns the name of the main class and the classpath for the application classloader.
 * The function is called via reflection and uses a class from JDK to store a pair to avoid dealing with classes from additional libraries in the classloader of the calling site.
 */
@Suppress("unused")
fun buildDevMain(): java.util.AbstractMap.SimpleImmutableEntry<String, Collection<Path>> {
  val info = buildDevImpl()

  @Suppress("SpellCheckingInspection")
  val exceptions = hashSetOf("jna.boot.library.path", "pty4j.preferred.native.folder", "jna.nosys", "jna.noclasspath", "jb.vmOptionsFile")
  val systemProperties = System.getProperties()
  for ((name, value) in info.systemProperties) {
    if (exceptions.contains(name) || !systemProperties.containsKey(name)) {
      systemProperties.setProperty(name, value)
    }
  }
  System.setProperty(PathManager.PROPERTY_HOME_PATH, info.systemProperties.getValue(PathManager.PROPERTY_HOME_PATH))

  // DevKitApplicationPatcher sets custom values for idea.config.path and idea.system.path only,
  // so we need to explicitly set idea.plugins.path and idea.log.path here to avoid a warning at runtime
  val configPath = systemProperties.get(PathManager.PROPERTY_CONFIG_PATH)
  if (configPath != null && !systemProperties.containsKey(PathManager.PROPERTY_PLUGINS_PATH)) {
    systemProperties.setProperty(PathManager.PROPERTY_PLUGINS_PATH, "$configPath${File.separator}plugins")
  }
  val systemPath = systemProperties.get(PathManager.PROPERTY_SYSTEM_PATH)
  if (systemPath != null && !systemProperties.containsKey(PathManager.PROPERTY_LOG_PATH)) {
    systemProperties.setProperty(PathManager.PROPERTY_LOG_PATH, "$systemPath${File.separator}log")
  }

  return java.util.AbstractMap.SimpleImmutableEntry(info.mainClassName, info.classPath)
}

@Suppress("IO_FILE_USAGE")
fun buildDevImpl(): BuildDevInfo {
  //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(File.separator, "/"))
  val additionalClassPaths = System.getProperty("idea.dev.additional.classpath")?.splitToSequence(',')?.map { Path.of(it) }?.toList() ?: emptyList()

  var homePath: String? = null
  var newClassPath: Collection<Path>? = null
  var mainClassName: String? = null
  val environment = mutableMapOf<String, String>()
  withTracer(serviceName = "builder") {
    val platformPrefix = System.getProperty("idea.platform.prefix", "idea")
    val isFrontendProcess = platformPrefix == "JetBrainsClient"
    val baseIdeForFrontendPropertyName = "dev.build.base.ide.platform.prefix.for.frontend"
    val baseIdePlatformPrefixForFrontend = System.getProperty(baseIdeForFrontendPropertyName)
    if (isFrontendProcess && baseIdePlatformPrefixForFrontend == null) {
      //todo make it error
      println("Warning: property '$baseIdeForFrontendPropertyName' must be specified in VM Options of the run configuration to select which variant of JetBrains Client should be started")
    }

    buildProductInProcess(
      BuildRequest(
        platformPrefix = platformPrefix,
        baseIdePlatformPrefixForFrontend = baseIdePlatformPrefixForFrontend,
        additionalModules = getAdditionalPluginMainModules(),
        projectDir = ideaProjectRoot,
        keepHttpClient = false,
        platformClassPathConsumer = { actualMainClassName, classPath, runDir ->
          mainClassName = actualMainClassName
          newClassPath = LinkedHashSet<Path>(classPath.size + additionalClassPaths.size).also {
            it.addAll(classPath)
            it.addAll(additionalClassPaths)
          }
          homePath = runDir.invariantSeparatorsPathString
          environment.putAll(getIdeSystemProperties(runDir).map)
        },
        // we should use a binary launcher for dev-mode
        isBootClassPathCorrect = System.getProperty("idea.dev.mode.in.process.build.boot.classpath.correct", "false").toBoolean(),
        generateRuntimeModuleRepository = System.getProperty("intellij.build.generate.runtime.module.repository").toBoolean(),
        buildOptionsTemplate = BuildOptions(),
      )
    )
  }
  homePath?.let {
    environment.put(PathManager.PROPERTY_HOME_PATH, it)
  }
  return BuildDevInfo(
    mainClassName = mainClassName!!,
    classPath = newClassPath!!,
    systemProperties = environment,
  )
}

private fun getAdditionalPluginMainModules(): List<String> {
  return System.getProperty("additional.modules")?.splitToSequence(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
}
