// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevMainImpl")
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.util.SystemProperties
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.telemetry.withTracer
import java.nio.file.Path

data class BuildDevInfo(
  val classPath: Collection<Path>,
  val systemProperties: Map<String, String>,
)

@Suppress("unused")
fun buildDevMain(): Collection<Path> {
  val info = buildDevImpl()

  @Suppress("SpellCheckingInspection")
  val exceptions = setOf("jna.boot.library.path", "pty4j.preferred.native.folder", "jna.nosys", "jna.noclasspath", "jb.vmOptionsFile")
  val systemProperties = System.getProperties()
  for ((name, value) in info.systemProperties) {
    if (exceptions.contains(name) || !systemProperties.containsKey(name)) {
      systemProperties.setProperty(name, value)
    }
  }
  System.setProperty(PathManager.PROPERTY_HOME_PATH, info.systemProperties.getValue(PathManager.PROPERTY_HOME_PATH))
  return info.classPath
}

@Suppress("IO_FILE_USAGE")
fun buildDevImpl(): BuildDevInfo {
  //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(java.io.File.separator, "/"))

  var homePath: String? = null
  var newClassPath: Collection<Path>? = null
  val environment = mutableMapOf<String, String>()
  withTracer(serviceName = "builder") {
    buildProductInProcess(
      BuildRequest(
        platformPrefix = System.getProperty("idea.platform.prefix", "idea"),
        additionalModules = getAdditionalPluginMainModules(),
        projectDir = ideaProjectRoot,
        keepHttpClient = false,
        platformClassPathConsumer = { classPath, runDir ->
          newClassPath = classPath
          homePath = runDir.toString().replace(java.io.File.separator, "/")
          environment.putAll(getIdeSystemProperties(runDir).map)
        },
        generateRuntimeModuleRepository = SystemProperties.getBooleanProperty("intellij.build.generate.runtime.module.repository", false),
        buildOptionsTemplate = BuildOptions(),
      )
    )
  }
  homePath?.let {
    environment[PathManager.PROPERTY_HOME_PATH] = it
  }
  return BuildDevInfo(
    classPath = newClassPath!!,
    systemProperties = environment,
  )
}

private fun getAdditionalPluginMainModules(): List<String> =
  System.getProperty("additional.modules")?.splitToSequence(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
