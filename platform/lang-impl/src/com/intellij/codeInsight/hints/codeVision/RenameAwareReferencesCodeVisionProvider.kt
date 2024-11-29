// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.REFACTORING_DATA_KEY
import com.intellij.refactoring.suggested.SuggestedRenameData
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent

/**
 * Similar to [ReferencesCodeVisionProvider] but not daemon based.
 *
 * This [CodeVisionProvider] skips PSI elements concerned by [SuggestedRenameData]
 * (stored in the file user data, see [REFACTORING_DATA_KEY]).
 */
abstract class RenameAwareReferencesCodeVisionProvider : CodeVisionProvider<Nothing?> {

  override fun precomputeOnUiThread(editor: Editor): Nothing? = null

  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default

  open fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
    GotoDeclarationAction.startFindUsages(editor, element.project, element, if (event == null) null else RelativePoint(event))
  }

  override fun computeCodeVision(editor: Editor, uiData: Nothing?): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.READY_EMPTY
    if (DumbService.isDumb(project)) return CodeVisionState.NotReady
    val cacheService = project.service<CodeVisionCacheService>()
    val cached = cacheService.getVisionDataForEditor(editor, id)
    val stamp = ModificationStampUtil.getModificationStamp(editor)
    if (stamp != null && cached?.modificationStamp == stamp) return CodeVisionState.Ready(cached.codeVisionEntries)

    return InlayHintsUtils.computeCodeVisionUnderReadAction {
      if (DumbService.isDumb(project)) return@computeCodeVisionUnderReadAction CodeVisionState.NotReady
      recomputeLenses(editor, project, stamp, cacheService)
    }
  }

  @RequiresReadLock
  private fun recomputeLenses(editor: Editor,
                              project: Project,
                              stamp: Long?,
                              cacheService: CodeVisionCacheService): CodeVisionState {
    if (DumbService.isDumb(project)) return CodeVisionState.READY_EMPTY
    val file = FileDocumentManager.getInstance().getFile(editor.document)?.findPsiFile(project) ?: return CodeVisionState.READY_EMPTY

    if (file.project.isDefault) return CodeVisionState.READY_EMPTY
    if (!acceptsFile(file)) return CodeVisionState.READY_EMPTY

    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest()) return CodeVisionState.READY_EMPTY

    val virtualFile = file.viewProvider.virtualFile
    if (ProjectFileIndex.getInstance(file.project).isInLibrarySource(virtualFile)) return CodeVisionState.READY_EMPTY

    val renamedElementToSkip = when (val refactoringData = file.getUserData(REFACTORING_DATA_KEY)) {
      is SuggestedRenameData -> refactoringData.declaration
      else -> null
    }

    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(file)
    for (element in traverser) {
      if (!acceptsElement(element)) continue
      if (element == renamedElementToSkip) continue
      if (!InlayHintsUtils.isFirstInLine(element)) continue
      val hint = getHint(element, file)
      if (hint == null) continue
      val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
      lenses.add(range to ClickableTextCodeVisionEntry(hint, id, { event, sourceEditor ->
        handleClick(sourceEditor, element, event)
        logClickToFUS(element, hint)
      }))
    }

    if (stamp != null) {
      cacheService.storeVisionDataForEditor(editor, id, CodeVisionCacheService.CodeVisionWithStamp(lenses, stamp))
    }
    return CodeVisionState.Ready(lenses)
  }

  open fun logClickToFUS(element: PsiElement, hint: @Nls String) {}

  abstract fun getHint(element: PsiElement, file: PsiFile): @Nls String?

  abstract fun acceptsElement(element: PsiElement): Boolean

  abstract fun acceptsFile(file: PsiFile): Boolean

  override val name: String
    get() = CodeInsightBundle.message("settings.inlay.hints.usages")
  override val groupId: String
    get() = PlatformCodeVisionIds.USAGES.key
}