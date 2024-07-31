// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionPredefinedActionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.codeVision.CodeVisionFusCollector
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.RefactoringCodeVisionSupport
import com.intellij.refactoring.suggested.REFACTORING_DATA_KEY
import com.intellij.refactoring.suggested.SuggestedRenameData
import com.intellij.refactoring.suggested.performSuggestedRefactoring
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls

class RenameCodeVisionProvider : CodeVisionProvider<Unit> {
  companion object {
    internal const val ID: String = "Rename refactoring"
  }

  override fun precomputeOnUiThread(editor: Editor) {}

  class RenameCodeVisionEntry(
    val project: Project,
    @Nls text: String,
    @NlsContexts.Tooltip tooltip: String,
    providerId: String,
  ) : TextCodeVisionEntry(text, providerId, AllIcons.Actions.SuggestedRefactoringBulb, tooltip, tooltip, listOf()), CodeVisionPredefinedActionEntry {
    override fun onClick(editor: Editor) {
      CodeVisionFusCollector.refactoringPerformed(CodeVisionFusCollector.Refactorings.Rename)
      val mouseEvent = this.getUserData(codeVisionEntryMouseEventKey)
      CommandProcessor.getInstance().executeCommand(project, {
        performSuggestedRefactoring(project,
                                    editor,
                                    null,
                                    mouseEvent?.point,
                                    false,
                                    ActionPlaces.EDITOR_INLAY)
      }, RefactoringBundle.message("rename.code.vision.command.name"), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, true)
    }
  }

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.READY_EMPTY

    return InlayHintsUtils.computeCodeVisionUnderReadAction {
      return@computeCodeVisionUnderReadAction when {
        project.isDisposed || editor.isDisposed -> CodeVisionState.READY_EMPTY
        else -> getCodeVisionState(editor, project)
      }
    }
  }

  @RequiresReadLock
  private fun getCodeVisionState(editor: Editor, project: Project): CodeVisionState {
    val file = editor.virtualFile?.findPsiFile(project)

    if (file != null && !RefactoringCodeVisionSupport.isRenameCodeVisionEnabled(file.fileType)) {
      return CodeVisionState.READY_EMPTY
    }

    val refactoring = file?.getUserData(REFACTORING_DATA_KEY)
                      ?: editor.getUserData(REFACTORING_DATA_KEY)
                      ?: return CodeVisionState.READY_EMPTY

    if (refactoring is SuggestedRenameData) {
      if (refactoring.oldName == refactoring.newName) return CodeVisionState.READY_EMPTY
      val text = RefactoringBundle.message("rename.code.vision.text")
      val tooltip = RefactoringBundle.message("suggested.refactoring.rename.popup.text", refactoring.oldName, refactoring.newName)
      return CodeVisionState.Ready(listOf(
        refactoring.declaration.textRange to RenameCodeVisionEntry(project, text, tooltip, id)
      ))
    }

    return CodeVisionState.READY_EMPTY
  }

  override fun preparePreview(editor: Editor, file: PsiFile) {
    val visitor = object : PsiRecursiveElementVisitor() {
      var renamedElement: PsiNamedElement? = null
      override fun visitElement(element: PsiElement) {
        if (element is PsiNamedElement && element.name == "foo2") renamedElement = element
        else super.visitElement(element)
      }
    }
    file.accept(visitor)
    visitor.renamedElement?.let { editor.putUserData(REFACTORING_DATA_KEY, SuggestedRenameData(it, "foo")) }
  }

  override val name: String
    get() = RefactoringBundle.message("rename.code.vision.label")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Top
  override val id: String
    get() = ID
  override val groupId: String
    get() = PlatformCodeVisionIds.RENAME.key

  override fun isAvailableFor(project: Project): Boolean {
    return FileTypeManager.getInstance().registeredFileTypes.any {
      RefactoringCodeVisionSupport.isRenameCodeVisionEnabled(it)
    }
  }
}