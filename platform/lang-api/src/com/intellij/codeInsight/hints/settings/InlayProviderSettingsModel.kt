// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Model of settings of single language hints provider (Settings/Preferences | Editor | Inlay Hints).
 *
 * @param isEnabled language is enabled in terms of [com.intellij.codeInsight.hints.InlayHintsSettings.hintsEnabled].
 * @param id unique model id
 * @param language model language
 */
abstract class InlayProviderSettingsModel(var isEnabled: Boolean, val id: String, val language: Language) {
  /**
   * Listener must be notified if any settings of inlay provider was changed
   */
  var onChangeListener: ChangeListener? = null

  /**
   * Name of provider to be displayed in list
   */
  abstract val name: @Nls String

  open val group: InlayGroup = InlayGroup.OTHER_GROUP

  /**
   *  Arbitrary component to be displayed in
   */
  abstract val component: JComponent

  /**
   * Called, when it is required to update inlay hints for file in preview
   * Invariant: if previewText == null, this method is not invoked
   *
   * The method itself is called in read action in background.
   *
   * Should not make any visible changes (run in nonBlockingReadAction)
   *
   * @return continuation which is run in EDT
   */
  open fun collectData(editor: Editor, file: PsiFile) : Runnable {
    return Runnable { collectAndApply(editor, file) }
  }

  open fun collectAndApply(editor: Editor, file: PsiFile) {

  }

  open fun createFile(project: Project, fileType: FileType, document: Document): PsiFile {
    val factory = PsiFileFactory.getInstance(project)
    return factory.createFileFromText("dummy", fileType, document.text)
  }

  /**
   * Short description to be displayed in the model's details
   */
  abstract val description: String?

  /**
   * Text of hints preview. If null, won't be shown.
   */
  abstract val previewText: String?

  /**
   * Code preview text for given case
   * @param case null for model node
   */
  abstract fun getCasePreview(case: ImmediateConfigurable.Case?): String?

  abstract fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language?

  /**
   * Description for given case
   * @param case case to get description
   */
  @Nls
  abstract fun getCaseDescription(case: ImmediateConfigurable.Case): String?

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

  var isMergedNode: Boolean = false

  @get:NlsContexts.Checkbox
  abstract val mainCheckBoxLabel: String

  /**
   * List of cases. If main check box is disabled, these checkboxes are also disabled
   */
  abstract val cases: List<ImmediateConfigurable.Case>
}