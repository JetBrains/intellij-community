// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevMainImpl")
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import com.intellij.platform.buildData.productInfo.ProductInfoData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.telemetry.withTracer
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

data class BuildDevInfo(
  @JvmField val mainClassName: String,
  @JvmField val classPath: Collection<Path>,
  @JvmField val systemProperties: Map<String, String>,
)

@Suppress("unused")
fun buildDevMain(): java.util.AbstractMap.SimpleImmutableEntry<String, Collection<Path>> {
  return buildDevMain(emptyArray())
}

/**
 * Returns the name of the main class and the classpath for the application classloader.
 * The function is called via reflection and uses a class from JDK to store a pair to avoid dealing with classes from additional libraries in the classloader of the calling site.
 */
@Suppress("unused")
fun buildDevMain(rawArgs: Array<String>): java.util.AbstractMap.SimpleImmutableEntry<String, Collection<Path>> {
  val info = buildDevImpl(rawArgs)

  val systemProperties = System.getProperties()
  for ((name, value) in info.systemProperties) {
    // don't override Rider/ReSharper properties if already set
    val isRelevantProperty = name.startsWith("rider.", ignoreCase = true) ||
                             name.startsWith("resharper.", ignoreCase = true) ||
                             name == "idea.platform.prefix" ||
                             name == "idea.suppressed.plugins.set.selector" ||
                             name == "awt.toolkit.name" // this one is resolved (rewritten) by JBR on startup, it shouldn't be updated after that
    if (isRelevantProperty && systemProperties.containsKey(name)) {
      continue
    }
    systemProperties.setProperty(name, value)
  }

  // obsolete, safe to delete in 263
  systemProperties.computeIfAbsent(PathManager.PROPERTY_PLUGINS_PATH) {
    systemProperties[PathManager.PROPERTY_CONFIG_PATH]?.let { "${it}/plugins" }
  }
  systemProperties.computeIfAbsent(PathManager.PROPERTY_LOG_PATH) {
    systemProperties[PathManager.PROPERTY_SYSTEM_PATH]?.let { "${it}/log" }
  }

  return java.util.AbstractMap.SimpleImmutableEntry(info.mainClassName, info.classPath)
}

private fun buildDevImpl(rawArgs: Array<String>): BuildDevInfo {
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = requireNotNull(PathManager.getHomeDirFor(PathManager::class.java)) { "Cannot find home directory" }
  System.setProperty("idea.dev.project.root", ideaProjectRoot.invariantSeparatorsPathString)
  val additionalClassPaths = System.getProperty("idea.dev.additional.classpath")?.splitToSequence(',')?.map { Path.of(it) }?.toList() ?: emptyList()

  return withTracer(serviceName = "builder") {
    val platformPrefix = System.getProperty("idea.platform.prefix", "idea")
    val isFrontendProcess = platformPrefix == "JetBrainsClient"
    val baseIdeForFrontendPropertyName = "dev.build.base.ide.platform.prefix.for.frontend"
    val baseIdePlatformPrefixForFrontend = System.getProperty(baseIdeForFrontendPropertyName)
    if (isFrontendProcess && baseIdePlatformPrefixForFrontend == null) {
      //todo make it error
      println("Warning: property '$baseIdeForFrontendPropertyName' must be specified in VM Options of the run configuration to select which variant of JetBrains Client should be started")
    }

    lateinit var platformMainClassName: String
    lateinit var platformClassPath: Set<Path>
    val request = BuildRequest(
      platformPrefix = platformPrefix,
      baseIdePlatformPrefixForFrontend = baseIdePlatformPrefixForFrontend,
      additionalModules = getAdditionalPluginMainModules(),
      projectDir = ideaProjectRoot,
      keepHttpClient = false,
      platformClassPathConsumer = { actualMainClassName, classPath, runDir ->
        platformMainClassName = actualMainClassName
        platformClassPath = classPath
      },
      // we should use a binary launcher for dev-mode
      isBootClassPathCorrect = System.getProperty("idea.dev.mode.in.process.build.boot.classpath.correct", "false").toBoolean(),
      generateRuntimeModuleRepository = System.getProperty("intellij.build.generate.runtime.module.repository").toBoolean(),
    )
    val runDir = buildProductInProcess(request)


    val newClassPath = LinkedHashSet<Path>(platformClassPath.size + additionalClassPaths.size).also {
      it.addAll(platformClassPath)
      it.addAll(additionalClassPaths)
    }

    val systemProperties = getIdeSystemProperties(runDir) +
                           VmProperties(mapOf(PathManager.PROPERTY_HOME_PATH to runDir.invariantSeparatorsPathString))

    if (System.getProperty("idea.dev.mode.custom.command", "false").toBoolean()) {
      val infoData = loadProductInfo(runDir)
      val launch = infoData.launch.single() // we do not generate multiarchitecture builds
      val firstArg = rawArgs.first()
      val command = launch.customCommands.find { it.commands.contains(firstArg) } ?: error("No custom command found for $firstArg")
      val commandSystemProperties = getCommandSystemProperties(runDir, command)

      BuildDevInfo(
        mainClassName = command.mainClass!!,
        classPath = newClassPath,
        systemProperties = (systemProperties + commandSystemProperties).map
      )
    }
    else {
      BuildDevInfo(
        mainClassName = platformMainClassName,
        classPath = newClassPath,
        systemProperties = systemProperties.map
      )
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
@ApiStatus.Internal
private fun loadProductInfo(runDir: Path): ProductInfoData {
  val json = Json { ignoreUnknownKeys = true }

  val productInfoPath = runDir.resolve("bin").resolve(PRODUCT_INFO_FILE_NAME)
  return productInfoPath.inputStream().buffered().use {
    json.decodeFromStream(ProductInfoData.serializer(), it)
  }
}

private fun getCommandSystemProperties(runDir: Path, command: CustomCommandLaunchData): VmProperties {
  val result = command.additionalJvmArguments.asSequence()
    .filter { it.startsWith("-D") }
    .map { it.removePrefix("-D") }
    .associateBy(
      { it.substringBefore('=') },
      { it.substringAfter('=', "")
        .replace($$"$IDE_HOME", runDir.pathString)
        .replace($$"$APP_PACKAGE/Contents", runDir.pathString)
        .apply { check('$' !in this) { "Unsubstituted macro in JVM argument: $it" } }
      })
  return VmProperties(result)
}

private fun getAdditionalPluginMainModules(): List<String> {
  return System.getProperty("additional.modules")?.splitToSequence(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
}
