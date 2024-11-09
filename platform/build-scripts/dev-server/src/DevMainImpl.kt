// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "ReplaceJavaStaticMethodWithKotlinAnalog")
@file:JvmName("DevMainImpl")
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.util.SystemProperties
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildOptions.Companion.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getAdditionalPluginMainModules
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.telemetry.withTracer
import java.io.File
import java.nio.file.Path

fun buildDevMain(): Collection<Path> {
  //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
  @Suppress("TestOnlyProblems")
  val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
  System.setProperty("idea.dev.project.root", ideaProjectRoot.toString().replace(File.separator, "/"))

  var homePath: String? = null
  var newClassPath: Collection<Path>? = null
  withTracer(serviceName = "builder") {
    buildProductInProcess(
      BuildRequest(
        platformPrefix = System.getProperty("idea.platform.prefix", "idea"),
        additionalModules = getAdditionalPluginMainModules(),
        projectDir = ideaProjectRoot,
        keepHttpClient = false,
        platformClassPathConsumer = { classPath, runDir ->
          newClassPath = classPath
          homePath = runDir.toString().replace(File.separator, "/")

          @Suppress("SpellCheckingInspection")
          val exceptions = setOf("jna.boot.library.path", "pty4j.preferred.native.folder", "jna.nosys", "jna.noclasspath", "jb.vmOptionsFile")
          val systemProperties = System.getProperties()
          for ((name, value) in getIdeSystemProperties(runDir).map) {
            if (exceptions.contains(name) || !systemProperties.containsKey(name)) {
              systemProperties.setProperty(name, value)
            }
          }
        },
        generateRuntimeModuleRepository = SystemProperties.getBooleanProperty("intellij.build.generate.runtime.module.repository", false),
        buildOptionsTemplate = BuildOptions(
          useCompiledClassesFromProjectOutput = SystemProperties.getBooleanProperty(INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK, true),
        ),
      )
    )
  }
  homePath?.let {
    System.setProperty(PathManager.PROPERTY_HOME_PATH, it)
  }
  return newClassPath!!
}