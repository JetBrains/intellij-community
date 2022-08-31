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
import kotlin.io.path.Path

@Serializable
data class Configuration(val products: Map<String, ProductConfiguration>)

@Serializable
data class ProductConfiguration(val modules: List<String>, @SerialName("class") val className: String)

class BuildServer(val homePath: Path) {
  private val outDir: Path = Path(
    System.getenv("CLASSES_DIR") ?: homePath.resolve("out/classes/production").toRealPath().toString()
  ).toAbsolutePath()
  private val configuration: Configuration

  private val platformPrefixToPluginBuilder = ConcurrentHashMap<String, Deferred<IdeBuilder>>()

  init {
    // for compatibility with local runs and runs on CI
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, outDir.parent.toString())

    val jsonFormat = Json { isLenient = true }
    configuration = jsonFormat.decodeFromString(Configuration.serializer(),
      Files.readString(homePath.resolve("build/dev-build-server.json")))
  }

  suspend fun checkOrCreateIdeBuilder(platformPrefix: String): IdeBuilder {
    platformPrefixToPluginBuilder.get(platformPrefix)?.let {
      return checkChangesIfNeeded(it)
    }

    val productConfiguration = configuration.products.get(platformPrefix)
                               ?: throw ConfigurationException("No production configuration for platform prefix `$platformPrefix`, " +
                                                               "please add to `dev-build-server.json` if needed")

    val ideBuilderDeferred = CompletableDeferred<IdeBuilder>()
    platformPrefixToPluginBuilder.putIfAbsent(platformPrefix, ideBuilderDeferred)?.let {
      ideBuilderDeferred.cancel()
      return checkChangesIfNeeded(it)
    }

    try {
      val ideBuilder = initialBuild(productConfiguration, homePath, outDir)
      ideBuilderDeferred.complete(ideBuilder)
      return ideBuilder
    }
    catch (e: Throwable) {
      ideBuilderDeferred.completeExceptionally(e)
      throw e
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
}