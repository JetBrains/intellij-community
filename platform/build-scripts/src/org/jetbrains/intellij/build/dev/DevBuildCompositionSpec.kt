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
  @JvmField val root: String,
  @JvmField val manifest: String,
  @JvmField val pluginClasspathPart: String? = null,
)

@Serializable
@ApiStatus.Internal
data class DevBuildCompositionSpec(
  @JvmField val version: Int = DEV_BUILD_COMPOSITION_SPEC_VERSION,
  @JvmField val expectedFragments: List<String>,
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
