// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

/**
 * Allows to exclude a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
@CompileStatic
final class PluginBundlingRestrictions {
  public static final PluginBundlingRestrictions NONE = new PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, false)

  /**
   * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
   */
  @NotNull
  final List<OsFamily> supportedOs

  /**
   * Change this value if the plugin works on some architectures only and
   * therefore don't need to be bundled with distributions for other architectures.
   */
  @NotNull
  final List<JvmArchitecture> supportedArch

  /**
   * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
   */
  final boolean includeInEapOnly

  PluginBundlingRestrictions(@NotNull List<OsFamily> supportedOs, @NotNull List<JvmArchitecture> supportedArch, boolean includeInEapOnly) {
    this.supportedOs = supportedOs
    this.supportedArch = supportedArch
    this.includeInEapOnly = includeInEapOnly

    if (supportedOs == OsFamily.ALL && supportedArch != JvmArchitecture.ALL) {
      throw new IllegalArgumentException("os-independent, but arch-dependent plugins are currently not supported by build scripts")
    }
  }

  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false

    PluginBundlingRestrictions that = (PluginBundlingRestrictions)o

    if (includeInEapOnly != that.includeInEapOnly) return false
    if (supportedArch != that.supportedArch) return false
    if (supportedOs != that.supportedOs) return false

    return true
  }

  int hashCode() {
    int result
    result = supportedOs.hashCode()
    result = 31 * result + supportedArch.hashCode()
    result = 31 * result + (includeInEapOnly ? 1 : 0)
    return result
  }
}
