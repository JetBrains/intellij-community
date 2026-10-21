// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.PluginLoadingProblem
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.productMode.ProductMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class CheckErrorsResult(
  @NlsSafe val loadingError: String? = null,
  val requiredPluginNames: Set<String> = emptySet(),
  val suggestToEnableRequiredPlugins: Boolean = false,
  val kind: PluginLoadingProblem.Kind = PluginLoadingProblem.Kind.OTHER,
  val severity: PluginLoadingProblem.Severity = PluginLoadingProblem.Severity.UNEXPECTED,
  val disabledDependencyId: PluginId? = null,
  val productMode: ProductMode? = null,
  @Serializable(with = PluginModuleIdSerializer::class)
  val unavailableModuleId: PluginModuleId? = null,
  val dependencyPluginId: PluginId? = null,
  val pluginPresent: Boolean = true,
  val problemsBySide: Map<PluginSource, CheckErrorsResult> = emptyMap(),
)

private object PluginModuleIdSerializer : KSerializer<PluginModuleId> {
  @Serializable
  private data class ModuleIdData(val name: String, val namespace: String)

  override val descriptor = ModuleIdData.serializer().descriptor

  override fun serialize(encoder: Encoder, value: PluginModuleId) {
    encoder.encodeSerializableValue(ModuleIdData.serializer(), ModuleIdData(value.name, value.namespace))
  }

  override fun deserialize(decoder: Decoder): PluginModuleId {
    val data = decoder.decodeSerializableValue(ModuleIdData.serializer())
    return PluginModuleId(data.name, data.namespace)
  }
}
