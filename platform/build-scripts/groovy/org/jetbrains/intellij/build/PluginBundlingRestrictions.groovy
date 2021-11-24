// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Allows to exclude a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
@CompileStatic
final class PluginBundlingRestrictions {
  public static final PluginBundlingRestrictions NONE = new PluginBundlingRestrictions(OsFamily.ALL, false)

  /**
   * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
   */
  final List<OsFamily> supportedOs

  /**
   * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
   */
  final boolean includeInEapOnly

  PluginBundlingRestrictions(List<OsFamily> supportedOs, boolean includeInEapOnly) {
    this.supportedOs = supportedOs
    this.includeInEapOnly = includeInEapOnly
  }
}
