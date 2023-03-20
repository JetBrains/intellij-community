// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.TracerProviderManager
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private class DevMainImpl {
  companion object {
    @JvmStatic
    fun build(): Collection<Path> {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing ~500KB JSON file
      TracerProviderManager.spanExporterProvider = { listOf(ConsoleSpanExporter()) }
      //TracerProviderManager.setOutput(Path.of(System.getProperty("user.home"), "trace.json"))
      try {
        val ideaProjectRoot = Path.of(PathManager.getHomePathFor(PathManager::class.java)!!)
        System.setProperty("idea.dev.project.root", ideaProjectRoot.invariantSeparatorsPathString)

        var homePath: String? = null
        return runBlocking(Dispatchers.Default) {
          var newClassPath: Collection<Path>? = null
          buildProductInProcess(BuildRequest(
            platformPrefix = System.getProperty("idea.platform.prefix") ?: "idea",
            additionalModules = getAdditionalModules()?.toList() ?: emptyList(),
            homePath = ideaProjectRoot,
            keepHttpClient = false,
            platformClassPathConsumer = { classPath, runDir ->
              newClassPath = classPath
              homePath = runDir.invariantSeparatorsPathString

              for ((name, value) in getIdeSystemProperties(runDir)) {
                System.setProperty(name, value)
              }
            },
          ))

          if (homePath != null) {
            System.setProperty(PathManager.PROPERTY_HOME_PATH, homePath!!)
          }
          newClassPath!!
        }
      }
      finally {
        TracerProviderManager.finish()
      }
    }
  }
}