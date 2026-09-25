// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus

/**
 * One entry of a platform module output that a layout patcher writes, stated as data.
 *
 * The build scripts write the entry with the patcher. A dev distribution writes the same bytes with an isolated action,
 * and the residual jar of [moduleName] takes the output as the patch of [path]. A label names a file of the checkout or
 * of a dev-launch repository, so the action reads no project model and downloads nothing.
 */
@ApiStatus.Internal
sealed interface DevPlatformEntryPatch {
  val moduleName: String
  val path: String

  /** The class [className] with every reference to [fromClass] remapped to [toClass]. The names are binary names. */
  data class RemappedClass(
    override val moduleName: String,
    val className: String,
    val fromClass: String,
    val toClass: String,
  ) : DevPlatformEntryPatch {
    override val path: String
      get() = className.replace('.', '/') + ".class"
  }

  /** The class [className] with the empty string constant of [methodName] replaced by the text of [valueLabel]. */
  data class InjectedString(
    override val moduleName: String,
    val className: String,
    val methodName: String,
    val valueLabel: String,
  ) : DevPlatformEntryPatch {
    override val path: String
      get() = className.replace('.', '/') + ".class"
  }

  /** The first [length] lowercase hex digits of the SHA-256 of [sourceLabel]. */
  data class Sha256Prefix(
    override val moduleName: String,
    override val path: String,
    val sourceLabel: String,
    val length: Int,
  ) : DevPlatformEntryPatch

  /** The fixed [content], with no line end added. */
  data class Text(
    override val moduleName: String,
    override val path: String,
    val content: String,
  ) : DevPlatformEntryPatch
}

/**
 * A layout patcher that states the platform entries it writes, see [DevPlatformEntryPatch].
 *
 * The plan generator reads [devPlatformEntryPatches] from the platform layout of a split product. So the dev
 * distribution packs the patched jars with the packer instead of with the `platform_lib` fragment.
 */
@ApiStatus.Internal
interface DevPlatformPatchOwner {
  /** The entries the patcher writes, in the order it writes them. */
  val devPlatformEntryPatches: List<DevPlatformEntryPatch>
}
