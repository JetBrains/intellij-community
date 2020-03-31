// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JComponent

/**
 * Model of settings of single language hints provider (Preferences | Editor | Inlay Hints)
 * @param isEnabled language is enabled in terms of InlayHintsSettings.hintsEnabled
 */
abstract class InlayProviderSettingsModel(var isEnabled: Boolean, val id: String) {
  /**
   * Listener must be notified if any settings of inlay provider was changed
   */
  var onChangeListener: ChangeListener? = null

  /**
   * Name of provider to be displayed in list
   */
  abstract val name: String

  /**
   *  Arbitrary component to be displayed in
   */
  abstract val component: JComponent

  /**
   * Called, when it is required to update inlay hints for file in preview
   * Invariant: if previewText == null, this method is not invoked
   */
  abstract fun collectAndApply(editor: Editor, file: PsiFile)

  /**
   * Text of hints preview. If null, won't be shown.
   */
  abstract val previewText: String?

  /**
   * Saves changed settings
   */
  abstract fun apply()

  /**
   * Checks, whether settings are different from stored ones
   */
  abstract fun isModified(): Boolean

  /**
   * Loads stored settings and replaces current ones
   */
  abstract fun reset()

  abstract val mainCheckBoxLabel: String

  /**
   * List of cases, if main check check box is disabled, these checkboxes are also disabled
   */
  abstract val cases: List<ImmediateConfigurable.Case>
}