// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.models.BuiltInFeature
import com.intellij.ide.customize.transferSettings.models.FeatureInfo
import com.intellij.ide.customize.transferSettings.models.PluginFeature
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PlatformUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Allows to register plugins of third-party products for importing from VSCode.
 */
interface VSCodePluginMapping {

  companion object {
    val EP_NAME: ExtensionPointName<VSCodePluginMapping> = ExtensionPointName("com.intellij.transferSettings.vscode.pluginMapping")
  }

  fun mapPlugin(pluginId: String): FeatureInfo?
}

open class VSCodePluginMappingBase(private val map: Map<String, FeatureInfo>) : VSCodePluginMapping {

  override fun mapPlugin(pluginId: String): FeatureInfo? {
    return map[pluginId]
  }
}

@Serializable
private data class FeatureData(
  val vsCodeId: String,
  val ideaId: String? = null,
  val ideaName: String,
  val builtIn: Boolean = false,
  val bundled: Boolean = false,
  val disabled: Boolean = false
)

private val logger = logger<CommonPluginMapping>()

internal class CommonPluginMapping : VSCodePluginMapping {

  // Note that the later files will override the data from the former.
  private fun getResourceMappings(): List<String> = when {
    PlatformUtils.isDataGrip() -> listOf("general.json", "dg.json")
    PlatformUtils.isIntelliJ() -> listOf("general.json", "ic.json")
    PlatformUtils.isPyCharm() -> listOf("general.json", "pc.json")
    PlatformUtils.isRubyMine() -> listOf("general.json", "rm.json")
    PlatformUtils.isRustRover() -> listOf("general.json", "rr.json")
    PlatformUtils.isRider() -> listOf("general.json", "rd.json")
    PlatformUtils.isWebStorm() -> listOf("general.json", "ws.json")
    else -> listOf("general.json")
  }

  val allPlugins by lazy {
    val resourceNames = getResourceMappings()
    val result = mutableMapOf<String, FeatureInfo>()
    for (resourceName in resourceNames) {
      logger.runAndLogException {
        @OptIn(ExperimentalSerializationApi::class)
        val features = this.javaClass.classLoader.getResourceAsStream("pluginData/$resourceName").use { file ->
          Json.decodeFromStream<List<FeatureData>>(file)
        }
        for (data in features) {
          val isBundled = data.bundled || data.builtIn
          val feature =
            if (isBundled) BuiltInFeature(null, data.ideaName)
            else {
              if (data.ideaId == null) {
                logger.error("Cannot determine IntelliJ plugin id for feature $data.")
                continue
              }
              PluginFeature(null, data.ideaId, data.ideaName)
            }
          result[data.vsCodeId.lowercase()] = feature
        }
      }
    }

    result
  }

  override fun mapPlugin(pluginId: String) = allPlugins[pluginId.lowercase()]
}

object PluginsMappings {

  fun pluginIdMap(pluginId: String): FeatureInfo? {

    for (mapping in VSCodePluginMapping.EP_NAME.extensionList) {
      val feature = mapping.mapPlugin(pluginId)
      if (feature != null) return feature
    }

    return null
  }
}
