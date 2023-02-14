// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope2
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.closeKtorClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Serializable
internal data class Configuration(@JvmField val products: Map<String, ProductConfiguration>)

@Serializable
internal data class ProductConfiguration(@JvmField val modules: List<String>, @JvmField @SerialName("class") val className: String)

private const val PRODUCTS_PROPERTIES_PATH = "build/dev-build.json"

suspend fun buildProductInProcess(request: BuildRequest) {
  TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).useWithScope2 {
    val platformPrefix = request.platformPrefix
    val configuration = createConfiguration(homePath = request.homePath, productionClassOutput = request.productionClassOutput)
    val productConfiguration = getProductConfiguration(configuration, platformPrefix)
    try {
      buildProduct(productConfiguration = productConfiguration, request = request, isServerMode = false)
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
  return Json.decodeFromString(Configuration.serializer(), Files.readString(homePath.resolve(PRODUCTS_PROPERTIES_PATH)))
}

private fun getProductConfiguration(configuration: Configuration, platformPrefix: String): ProductConfiguration {
  return configuration.products.get(platformPrefix) ?: throw ConfigurationException(
    "No production configuration for platform prefix `$platformPrefix` please add to `$PRODUCTS_PROPERTIES_PATH` if needed"
  )
}

internal class BuildServer(homePath: Path, productionClassOutput: Path) {
  private val configuration = createConfiguration(productionClassOutput, homePath)
  private val platformPrefixToPluginBuilder = ConcurrentHashMap<String, Deferred<IdeBuilder>>()

  suspend fun checkOrCreateIdeBuilder(request: BuildRequest): IdeBuilder {
    platformPrefixToPluginBuilder.get(request.platformPrefix)?.let {
      return checkChangesIfNeeded(it)
    }

    val ideBuilderDeferred = CompletableDeferred<IdeBuilder>()
    platformPrefixToPluginBuilder.putIfAbsent(request.platformPrefix, ideBuilderDeferred)?.let {
      ideBuilderDeferred.cancel()
      return checkChangesIfNeeded(it)
    }

    try {
      val platformPrefix = request.platformPrefix
      return buildProduct(productConfiguration = getProductConfiguration(configuration, platformPrefix),
                          request = request,
                          isServerMode = true)
    }
    catch (e: Throwable) {
      ideBuilderDeferred.completeExceptionally(e)
      throw e
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun checkChangesIfNeeded(ideBuilderDeferred: Deferred<IdeBuilder>): IdeBuilder {
  if (ideBuilderDeferred.isActive) {
    return ideBuilderDeferred.await()
  }
  else {
    val ideBuilder = ideBuilderDeferred.getCompleted()
    ideBuilder.checkChanged()
    return ideBuilder
  }
}

internal class ConfigurationException(message: String) : RuntimeException(message)