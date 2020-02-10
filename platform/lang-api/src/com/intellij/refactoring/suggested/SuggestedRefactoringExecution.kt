// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.RefactoringFactory

/**
 * A service performing actual changes in the code when user executes suggested refactoring.
 */
abstract class SuggestedRefactoringExecution(protected val refactoringSupport: SuggestedRefactoringSupport) {
    open fun rename(data: SuggestedRenameData, project: Project, editor: Editor) {
        val relativeCaretOffset = editor.caretModel.offset - anchorOffset(data.declaration)

        val newName = data.declaration.name!!
        runWriteAction {
            data.declaration.setName(data.oldName)
        }

        var refactoring = RefactoringFactory.getInstance(project).createRename(data.declaration, newName, true, true)
        refactoring.respectEnabledAutomaticRenames()

        val usages = refactoring.findUsages()
        if (usages.any { it.isNonCodeUsage } && !ApplicationManager.getApplication().isHeadlessEnvironment) {
            val question = RefactoringBundle.message(
              "suggested.refactoring.rename.comments.strings.occurrences",
              data.oldName,
              newName
            )
            val result = Messages.showOkCancelDialog(
              project,
              question,
              RefactoringBundle.message("suggested.refactoring.rename.comments.strings.occurrences.title"),
              RefactoringBundle.message("suggested.refactoring.rename.with.preview.button.text"),
              RefactoringBundle.message("suggested.refactoring.ignore.button.text"),
              Messages.getQuestionIcon()
            )
            if (result != Messages.OK) {
                refactoring = RefactoringFactory.getInstance(project).createRename(data.declaration, newName, false, false)
                refactoring.respectEnabledAutomaticRenames()
            }
        }

        refactoring.run()

        if (data.declaration.isValid) {
            editor.caretModel.moveToOffset(relativeCaretOffset + anchorOffset(data.declaration))
        }
    }
    
    open fun changeSignature(
        data: SuggestedChangeSignatureData,
        newParameterValues: List<NewParameterValue>,
        project: Project,
        editor: Editor
    ) {
        val preparedData = prepareChangeSignature(data)

        val relativeCaretOffset = editor.caretModel.offset - anchorOffset(data.declaration)

        val restoreNewSignature = runWriteAction {
            data.restoreInitialState(refactoringSupport)
        }

        performChangeSignature(data, newParameterValues, preparedData)

        runWriteAction {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            restoreNewSignature()
            editor.caretModel.moveToOffset(relativeCaretOffset + anchorOffset(data.declaration))
        }
    }

    /**
     * Prepares data for Change Signature refactoring while the declaration has state with user changes (the new signature).
     */
    abstract fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any?

    /**
     * Performs Change Signature refactoring. This method is invoked with the declaration reverted to its original state
     * before user changes (the old signature). The list of imports is also reverted to the original state.
     */
    abstract fun performChangeSignature(data: SuggestedChangeSignatureData, newParameterValues: List<NewParameterValue>, preparedData: Any?)

    private fun anchorOffset(declaration: PsiElement): Int {
        return refactoringSupport.nameRange(declaration)?.startOffset ?: declaration.startOffset
    }

    /**
     * Use this implementation of [SuggestedRefactoringExecution], if only Rename refactoring is supported for the language.
     */
    open class RenameOnly(refactoringSupport: SuggestedRefactoringSupport) : SuggestedRefactoringExecution(refactoringSupport) {
        override fun performChangeSignature(
            data: SuggestedChangeSignatureData,
            newParameterValues: List<NewParameterValue>,
            preparedData: Any?
        ) {
            throw UnsupportedOperationException()
        }

        override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any? {
            throw UnsupportedOperationException()
        }
    }

    /**
     * Class representing value for a new parameter to be used for updating arguments of calls.
     */
    sealed class NewParameterValue {
        object None : NewParameterValue()
        data class Expression(val expression: PsiElement) : NewParameterValue()
        object AnyVariable : NewParameterValue()
    }
}