// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.closeKtorClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

@Serializable
internal data class Configuration(@JvmField val products: Map<String, ProductConfiguration>)

@Serializable
internal data class ProductConfiguration(@JvmField val modules: List<String>, @JvmField @SerialName("class") val className: String)

private const val PRODUCTS_PROPERTIES_PATH = "build/dev-build.json"

/**
 * Custom path for product properties
 */
private const val CUSTOM_PRODUCT_PROPERTIES_PATH = "idea.product.properties.path"

@Suppress("SpellCheckingInspection")
fun getIdeSystemProperties(runDir: Path): Map<String, String> {
  // see BuildContextImpl.getAdditionalJvmArguments - we should somehow deduplicate code
  val libDir = runDir.resolve("lib")

  val defaultProperties: Map<String, String> = Properties().apply {
    load(runDir.resolve("bin/idea.properties").bufferedReader())
  }.map { it.key.toString() to it.value.toString() }.toMap()

  return defaultProperties.plus(
    mapOf(
      "jna.boot.library.path" to "$libDir/jna/${JvmArchitecture.currentJvmArch.dirName}",
      "pty4j.preferred.native.folder" to "$libDir/pty4j",
      // require bundled JNA dispatcher lib
      "jna.nosys" to "true",
      "jna.noclasspath" to "true",
      "jb.vmOptionsFile" to "${runDir.parent.listDirectoryEntries(glob = "*.vmoptions").singleOrNull()}"
  ))
}

/** Returns IDE installation directory */
suspend fun buildProductInProcess(request: BuildRequest): Path {
  return TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).useWithScope {
    val platformPrefix = request.platformPrefix
    val configuration = createConfiguration(homePath = request.homePath, productionClassOutput = request.productionClassOutput)
    val productConfiguration = getProductConfiguration(configuration, platformPrefix)
    try {
      buildProduct(productConfiguration = productConfiguration, request = request)
    }
    finally {
      // otherwise, a thread leak in tests
      if (!request.keepHttpClient) {
        withContext(NonCancellable) {
          closeKtorClient()
        }
      }
    }
  }
}

private fun createConfiguration(productionClassOutput: Path, homePath: Path): Configuration {
  // for compatibility with local runs and runs on CI
  System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, productionClassOutput.parent.toString())
  val projectPropertiesPath = getProductPropertiesPath(homePath)
  return Json.decodeFromString(Configuration.serializer(), Files.readString(projectPropertiesPath))
}

private fun getProductPropertiesPath(homePath: Path): Path {
  // Handle custom product properties path
  val customPath = System.getProperty(CUSTOM_PRODUCT_PROPERTIES_PATH)?.let { homePath.resolve(it) }
  if (customPath != null && customPath.exists()) {
    return customPath
  }
  return homePath.resolve(PRODUCTS_PROPERTIES_PATH)
}

private fun getProductConfiguration(configuration: Configuration, platformPrefix: String): ProductConfiguration {
  return configuration.products.get(platformPrefix) ?: throw ConfigurationException(
    "No production configuration for platform prefix `$platformPrefix` please add to `$PRODUCTS_PROPERTIES_PATH` if needed"
  )
}

internal class ConfigurationException(message: String) : RuntimeException(message)
