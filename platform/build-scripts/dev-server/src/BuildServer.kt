// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import java.nio.file.Files
import java.nio.file.Path
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

  private val platformPrefixToPluginBuilder = HashMap<String, IdeBuilder>()

  init {
    // for compatibility with local runs and runs on CI
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, outDir.parent.toString())

    val jsonFormat = Json { isLenient = true }
    configuration = jsonFormat.decodeFromString(Configuration.serializer(),
      Files.readString(homePath.resolve("build/dev-build-server.json")))
  }

  @Synchronized
  fun checkOrCreateIdeBuilder(platformPrefix: String): IdeBuilder {
    var ideBuilder = platformPrefixToPluginBuilder[platformPrefix]
    if (ideBuilder != null) {
      ideBuilder.checkChanged()
      return ideBuilder
    }

    val productConfiguration = configuration.products[platformPrefix]
                               ?: throw ConfigurationException("No production configuration for platform prefix `$platformPrefix`, " +
                                                               "please add to `dev-build-server.json` if needed")

    ideBuilder = initialBuild(productConfiguration, homePath, outDir)
    platformPrefixToPluginBuilder[platformPrefix] = ideBuilder
    return ideBuilder
  }
}