// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.model.Pointer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.impl.showDialogAndRename

/**
 * Shows Rename dialog when the [RenameElementAction] is invoked on an inplace template segment
 * while the inplace rename is in progress.
 */
internal class InplaceRenameContinuation(
  private val targetPointer: Pointer<out RenameTarget>
) : InplaceRefactoringContinuation {

  override fun getRefactoringKey(): Any = RenameElementAction::class.java

  override fun resumeRefactoring(project: Project, editor: Editor): Boolean {
    val templateState: TemplateState = TemplateManagerImpl.getTemplateState(editor)
                                       ?: return false
    if (!isInvokedOnASegment(templateState, editor.caretModel.offset)) {
      return false
    }
    val target: RenameTarget = targetPointer.dereference()
                               ?: return false
    val newName: String = templateState.getVariableValue(InplaceRefactoring.PRIMARY_VARIABLE_NAME)?.text
                          ?: return false
    templateState.gotoEnd(true)
    showDialogAndRename(project, target, newName)
    return true
  }

  private fun isInvokedOnASegment(templateState: TemplateState, offset: Int): Boolean {
    for (i in 0 until templateState.segmentsCount) {
      val segmentRange = templateState.getSegmentRange(i)
      if (segmentRange.containsOffset(offset)) {
        return true
      }
    }
    return false
  }
}
