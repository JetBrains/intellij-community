// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devServer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Serializable
internal data class Configuration(@JvmField val products: Map<String, ProductConfiguration>)

@Serializable
internal data class ProductConfiguration(@JvmField val modules: List<String>, @JvmField @SerialName("class") val className: String)

private const val PRODUCTS_PROPERTIES_PATH = "build/dev-build.json"

internal class BuildServer(homePath: Path, productionClassOutput: Path) {
  private val configuration: Configuration

  private val platformPrefixToPluginBuilder = ConcurrentHashMap<String, Deferred<IdeBuilder>>()

  init {
    // for compatibility with local runs and runs on CI
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, productionClassOutput.parent.toString())

    val jsonFormat = Json { isLenient = true }
    configuration = jsonFormat.decodeFromString(Configuration.serializer(), Files.readString(homePath.resolve(PRODUCTS_PROPERTIES_PATH)))
  }

  // not synchronized version
  suspend fun buildProductInProcess(isServerMode: Boolean, request: BuildRequest): IdeBuilder {
    val platformPrefix = request.platformPrefix
    return buildProduct(productConfiguration = getProductConfiguration(platformPrefix), request = request, isServerMode = isServerMode)
  }

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
      return buildProductInProcess(isServerMode = true, request = request)
    }
    catch (e: Throwable) {
      ideBuilderDeferred.completeExceptionally(e)
      throw e
    }
  }

  private fun getProductConfiguration(platformPrefix: String): ProductConfiguration {
    return configuration.products.get(platformPrefix) ?: throw ConfigurationException(
      "No production configuration for platform prefix `$platformPrefix` please add to `$PRODUCTS_PROPERTIES_PATH` if needed"
    )
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
}

internal class ConfigurationException(message: String) : RuntimeException(message)