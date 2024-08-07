// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature

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
import com.intellij.refactoring.suggested.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls

class ChangeSignatureCodeVisionProvider : CodeVisionProvider<Unit> {
  companion object {
    internal const val ID: String = "Change signature"
  }

  override fun precomputeOnUiThread(editor: Editor) {}

  class ChangeSignatureCodeVisionEntry(
    val project: Project,
    @Nls text: String,
    @NlsContexts.Tooltip tooltip: String,
    providerId: String,
  ) : TextCodeVisionEntry(text, providerId, AllIcons.Actions.SuggestedRefactoringBulb, tooltip, tooltip, listOf()), CodeVisionPredefinedActionEntry {
    override fun onClick(editor: Editor) {
      CodeVisionFusCollector.refactoringPerformed(CodeVisionFusCollector.Refactorings.ChangeSignature)
      val mouseEvent = this.getUserData(codeVisionEntryMouseEventKey)
      CommandProcessor.getInstance().executeCommand(project, {
        performSuggestedRefactoring(project,
                                    editor,
                                    null,
                                    mouseEvent?.point,
                                    true,
                                    ActionPlaces.EDITOR_INLAY)
      }, RefactoringBundle.message("change.signature.code.vision.name"), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, true)
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

    if (file != null && !RefactoringCodeVisionSupport.isChangeSignatureCodeVisionEnabled(file.fileType)) {
      return CodeVisionState.READY_EMPTY
    }

    val refactoring = file?.getUserData(REFACTORING_DATA_KEY)
                      ?: editor.getUserData(REFACTORING_DATA_KEY)
                      ?: return CodeVisionState.READY_EMPTY

    if (refactoring is SuggestedChangeSignatureData) {
      val text = RefactoringBundle.message("change.signature.code.vision.text")
      val tooltip = RefactoringBundle.message(
        "suggested.refactoring.change.signature.gutter.icon.tooltip",
        refactoring.nameOfStuffToUpdate,
        refactoring.oldSignature.name,
        ""
      )
      val element = refactoring.declarationPointer.element
      if (element != null) {
        return CodeVisionState.Ready(listOf(
          element.textRange to ChangeSignatureCodeVisionEntry(project, text, tooltip, id)
        ))
      }
    }

    return CodeVisionState.READY_EMPTY
  }

  override fun preparePreview(editor: Editor, file: PsiFile) {
    val visitor = object : PsiRecursiveElementVisitor() {
      var refactoredElement: PsiNamedElement? = null
      override fun visitElement(element: PsiElement) {
        if (refactoredElement == null && element is PsiNamedElement && element.name == "foo") refactoredElement = element
        else super.visitElement(element)
      }
    }
    file.accept(visitor)
    val support = SuggestedRefactoringSupport.forLanguage(file.language) ?: return
    visitor.refactoredElement?.let {
      val state = SuggestedRefactoringState(
        it,
        support,
        SuggestedRefactoringState.ErrorLevel.NO_ERRORS,
        "foo", "",
        SuggestedRefactoringSupport.Signature.create("foo", "", listOf(), null)!!,
        SuggestedRefactoringSupport.Signature.create("foo", "", listOf(), null)!!,
        listOf()
      )
      val data = SuggestedChangeSignatureData.create(state, "foo")
      editor.putUserData(REFACTORING_DATA_KEY, data)
    }
  }

  override val name: String
    get() = RefactoringBundle.message("change.signature.code.vision.text")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Top
  override val id: String
    get() = ID
  override val groupId: String
    get() = PlatformCodeVisionIds.CHANGE_SIGNATURE.key

  override fun isAvailableFor(project: Project): Boolean {
    return FileTypeManager.getInstance().registeredFileTypes.any {
      RefactoringCodeVisionSupport.isChangeSignatureCodeVisionEnabled(it)
    }
  }
}