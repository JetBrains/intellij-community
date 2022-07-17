// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JComponent

interface InlayGroupSettingProvider {

  val group: InlayGroup

  var isEnabled: Boolean

  /**
   *  Arbitrary component to be displayed in
   */
  val component: JComponent

  /**
   * Saves changed settings
   */
  fun apply()

  /**
   * Checks, whether settings are different from stored ones
   */
  fun isModified(): Boolean

  /**
   * Loads stored settings and replaces current ones
   */
  fun reset()

  object EP {
    val EXTENSION_POINT_NAME =
      ExtensionPointName.create<InlayGroupSettingProvider>("com.intellij.config.inlayGroupSettingProvider")

    fun findForGroup(group: InlayGroup) = EXTENSION_POINT_NAME.extensions.singleOrNull { it.group == group }
  }
}