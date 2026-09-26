package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/** One catalogue artifact of a plan file, or a path under a directory artifact. */
@ApiStatus.Internal
@Serializable
data class DevPluginReference(
  @JvmField val artifact: String,
  @JvmField val path: String = "",
)
