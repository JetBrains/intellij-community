// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

/**
 * Allows to exclude a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
data class PluginBundlingRestrictions(

  /**
   * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
   */
  val supportedOs: List<OsFamily>,

  /**
   * Change this value if the plugin works on some architectures only and
   * therefore don't need to be bundled with distributions for other architectures.
   */
  val supportedArch: List<JvmArchitecture>,

  /**
   * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
   */
  val includeInEapOnly: Boolean,
) {
  companion object {
    val NONE = PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, false)
  }

  init {
    if (supportedOs == OsFamily.ALL && supportedArch != JvmArchitecture.ALL) {
      error("os-independent, but arch-dependent plugins are currently not supported by build scripts")
    }
  }
}
