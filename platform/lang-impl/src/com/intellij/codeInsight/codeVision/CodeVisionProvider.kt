// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionPredefinedActionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * If invalidation and calculation should be bound to daemon,
 * then use [com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider]
 * Otherwise implement [CodeVisionProvider] directly.
 *
 * If you want to implement multiple providers with same meaning (for example, for different languages)
 * and group them in the settings window, then see [com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider].
 * @see [PlatformCodeVisionIds]
 * @see CodeVisionProviderFactory
 */
@ApiStatus.Experimental
interface CodeVisionProvider<T> {
  companion object {
    const val EP_NAME: String = "com.intellij.codeInsight.codeVisionProvider"
    val providersExtensionPoint: ExtensionPointName<CodeVisionProvider<*>> = ExtensionPointName.create(EP_NAME)
  }

  /**
   * WARNING! Must work very fast, it is invoked for a file during its opening. The user won't see the file's content if this method works long.
   * @return true iff it could potentially provide any lenses for the project
   */
  fun isAvailableFor(project: Project): Boolean = true

  /**
   * Prepares data for preview (to understand later that it is a preview), for example, stores user data in the editor/file.
   */
  fun preparePreview(editor: Editor, file: PsiFile) {
  }

  /**
   * Computes some data on UI thread before the background thread invocation.
   */
  fun precomputeOnUiThread(editor: Editor): T

  /**
   *  Called during the code vision update process.
   *
   *  @return true if [computeForEditor] should be called, false otherwise
   */
  fun shouldRecomputeForEditor(editor: Editor, uiData: T?): Boolean = true

  /**
   * Returns text ranges and applicable hints for them, invoked on background thread.
   *
   * Note that this method is not executed under read action.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use computeCodeVision instead", ReplaceWith("computeCodeVision"))
  fun computeForEditor(editor: Editor, uiData: T): List<Pair<TextRange, CodeVisionEntry>> = emptyList()

  /**
   * Returns text ranges and applicable hints for them, invoked on background thread.
   *
   * Note that this method is not executed under read action.
   */
  fun computeCodeVision(editor: Editor, uiData: T): CodeVisionState = CodeVisionState.Ready(computeForEditor(editor, uiData))

  /**
   * Handles click on a lens at a given range.
   *
   * [java.awt.event.MouseEvent] is accessible with [codeVisionEntryMouseEventKey] data key from [CodeVisionEntry].
   */
  fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry){
    if (entry is CodeVisionPredefinedActionEntry) entry.onClick(editor)
  }

  /**
   * Handles click on an extra action on a lens at a given range.
   */
  fun handleExtraAction(editor: Editor, textRange: TextRange, actionId: String): Unit = Unit

  fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?) : CodeVisionPlaceholderCollector? = null

  /**
   * User-visible name.
   */
  @get:Nls
  val name: String

  /**
   * Used for the default sorting of providers.
   */
  val relativeOrderings: List<CodeVisionRelativeOrdering>

  /**
   * Specifies default anchor for this provider.
   */
  val defaultAnchor: CodeVisionAnchorKind

  /**
   * Internal ID.
   */
  val id: String


  /**
   * Used to group provider in the settings panel and to share same behavior (like position and ext.) and description.
   * To group different provider implement [com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider].
   * @see PlatformCodeVisionIds
   */
  val groupId: String
    get() = id
}