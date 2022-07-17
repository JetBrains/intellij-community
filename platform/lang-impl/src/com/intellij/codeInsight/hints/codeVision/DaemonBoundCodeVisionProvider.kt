// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionPlaceholderCollector
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionPredefinedActionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Almost the same thing as [com.intellij.codeInsight.codeVision.CodeVisionProvider], but run in the [com.intellij.codeInsight.daemon.DaemonCodeAnalyzer]
 * and that's why it has built-in support of interruption.
 */
interface DaemonBoundCodeVisionProvider {
  companion object {
    const val EP_NAME = "com.intellij.codeInsight.daemonBoundCodeVisionProvider"
    val extensionPoint = ExtensionPointName.create<DaemonBoundCodeVisionProvider>(EP_NAME)
  }

  @JvmDefault
  fun preparePreview(editor: Editor, file: PsiFile) {

  }

  /**
   * Computes code lens data in read action in background for a given editor.
   */
  @Deprecated("Use overload with file")
  fun computeForEditor(editor: Editor): List<Pair<TextRange, CodeVisionEntry>> = emptyList()

  /**
   * Computes code lens data in read action in background for a given editor.
   */
  @Suppress("DEPRECATION")
  @JvmDefault
  fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> = emptyList()

  fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry){
    if (entry is CodeVisionPredefinedActionEntry) entry.onClick(editor)
  }

  /**
   * Calls on background BEFORE editor opening
   * Returns ranges where placeholders should be when editor opens
   */
  @JvmDefault
  @Deprecated("use getPlaceholderCollector")
  fun collectPlaceholders(editor: Editor): List<TextRange> = emptyList()

  @JvmDefault
  fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? = null

  /**
   * Name in settings.
   */
  @get:Nls
  val name: String

  val relativeOrderings: List<CodeVisionRelativeOrdering>

  val defaultAnchor: CodeVisionAnchorKind

  /**
   * Unique identifier (among all instances of [DaemonBoundCodeVisionProvider] and [com.intellij.codeInsight.codeVision.CodeVisionProvider])
   */
  @get:NonNls
  val id: String

  val groupId: String
    get() = id
}