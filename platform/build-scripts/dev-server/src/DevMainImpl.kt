// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevMainImpl")
@file:Suppress("IO_FILE_USAGE", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.telemetry.withTracer
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

data class BuildDevInfo(
  @JvmField val mainClassName: String,
  @JvmField val classPath: Collection<Path>,
  @JvmField val systemProperties: Map<String, String>,
)

/**
 * Returns the name of the main class and the classpath for the application classloader.
 * The function is called via reflection and uses a class from JDK to store a pair to avoid dealing with classes from additional libraries in the classloader of the calling site.
 */
@Suppress("unused")
fun buildDevMain(): java.util.AbstractMap.SimpleImmutableEntry<String, Collection<Path>> {
  val info = buildDevImpl()

  val systemProperties = System.getProperties()
  for ((name, value) in info.systemProperties) {
    // don't override Rider/ReSharper properties if already set
    val isRelevantProperty = name.startsWith("rider.", ignoreCase = true) ||
                             name.startsWith("resharper.", ignoreCase = true) ||
                             name == "idea.platform.prefix" ||
                             name == "idea.suppressed.plugins.set.selector"
    if (isRelevantProperty && systemProperties.containsKey(name)) {
      continue
    }
    systemProperties.setProperty(name, value)
  }

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
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = requireNotNull(PathManager.getHomeDirFor(PathManager::class.java)) { "Cannot find home directory" }
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(File.separator, "/"))
  val additionalClassPaths = System.getProperty("idea.dev.additional.classpath")?.splitToSequence(',')?.map { Path.of(it) }?.toList() ?: emptyList()

  var buildDevInfo: BuildDevInfo? = null
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
          val newClassPath = LinkedHashSet<Path>(classPath.size + additionalClassPaths.size).also {
            it.addAll(classPath)
            it.addAll(additionalClassPaths)
          }
          buildDevInfo = BuildDevInfo(
            mainClassName = actualMainClassName,
            classPath = newClassPath,
            systemProperties = (getIdeSystemProperties(runDir) + VmProperties(mapOf(PathManager.PROPERTY_HOME_PATH to runDir.invariantSeparatorsPathString))).map
          )
        },
        // we should use a binary launcher for dev-mode
        isBootClassPathCorrect = System.getProperty("idea.dev.mode.in.process.build.boot.classpath.correct", "false").toBoolean(),
        generateRuntimeModuleRepository = System.getProperty("intellij.build.generate.runtime.module.repository").toBoolean(),
        buildOptionsTemplate = BuildOptions(),
      )
    )
  }
  return buildDevInfo!!
}

private fun getAdditionalPluginMainModules(): List<String> {
  return System.getProperty("additional.modules")?.splitToSequence(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
}
