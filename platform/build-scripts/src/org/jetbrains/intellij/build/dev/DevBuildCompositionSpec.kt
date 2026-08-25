// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

private const val DEV_BUILD_COMPOSITION_SPEC_VERSION = 1

@Serializable
@ApiStatus.Internal
data class DevBuildCompositionComponent(
  /**
   * The component's tree, or `null` for a component whose manifest names where each of its files' bytes already are.
   *
   * See `DevBuildComponentEntry.source`: a producer of nothing but placements for already-packed jars declares no tree,
   * so there is none to name here.
   */
  @JvmField val root: String? = null,
  @JvmField val manifest: String,
  @JvmField val pluginClasspathPart: String? = null,
)

@Serializable
@ApiStatus.Internal
data class DevBuildCompositionSpec(
  @JvmField val version: Int = DEV_BUILD_COMPOSITION_SPEC_VERSION,
  @JvmField val expectedFragments: List<String>,
  /**
   * The plugin modules the distribution declares it contains, for `DevIdeConfig`.
   *
   * Stated by the distribution rather than summed over [components] on purpose: a module the product bundles is packed
   * by a plugin fragment several distributions share, and that fragment cannot know which of them asked for it. The
   * composer checks this against what the components report, so the two cannot drift apart silently.
   */
  @JvmField val additionalModules: List<String> = emptyList(),
  @JvmField val components: List<DevBuildCompositionComponent>,
  @JvmField val pluginClasspathPrefix: String? = null,
)

private val compositionSpecJson = Json { ignoreUnknownKeys = false }

@ApiStatus.Internal
fun readDevBuildCompositionSpec(file: Path): DevBuildCompositionSpec {
  val spec = compositionSpecJson.decodeFromString(DevBuildCompositionSpec.serializer(), Files.readString(file))
  check(spec.version == DEV_BUILD_COMPOSITION_SPEC_VERSION) {
    "Unsupported dev-build composition spec version ${spec.version} in $file"
  }
  check(spec.components.isNotEmpty()) { "Dev-build composition spec in $file has no components" }
  return spec
}
