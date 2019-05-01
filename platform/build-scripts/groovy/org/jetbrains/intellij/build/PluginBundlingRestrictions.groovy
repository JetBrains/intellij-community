// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Allows to exclude a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
@CompileStatic
class PluginBundlingRestrictions {
  /**
   * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
   */
  List<OsFamily> supportedOs = OsFamily.ALL

  /**
   * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
   */
  boolean includeInEapOnly = false
}
