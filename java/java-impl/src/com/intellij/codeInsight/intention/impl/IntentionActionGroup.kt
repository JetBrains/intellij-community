// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler.chooseActionAndInvoke
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

/**
 * This action represents a group of similar actions with ability to choose one.
 * - if all actions in group are unavailable, then this action becomes unavailable too;
 * - if one action in group is available, then this action uses its text and invokes it without [choosing][chooseAction];
 * - otherwise [getGroupText] and [chooseAction] are used.
 *
 * @param T type of actions
 */
abstract class IntentionActionGroup<T : IntentionAction>(private val actions: List<T>) : BaseIntentionAction() {

  final override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = null

  final override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null) return false

    val availableActions = actions.filter { it.isAvailable(project, editor, file) }
    if (availableActions.isEmpty()) return false

    text = availableActions.singleOrNull()?.text ?: getGroupText(availableActions)
    return true
  }

  /**
   * @param actions list of available actions with 2 elements minimum
   * @return text of this action
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  protected abstract fun getGroupText(actions: List<T>): String

  final override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) return

    val availableActions = actions.filter { it.isAvailable(project, editor, file) }
    if (availableActions.isEmpty()) return

    fun invokeAction(action: IntentionAction) {
      chooseActionAndInvoke(file, editor, action, action.text)
    }

    val singleAction = availableActions.singleOrNull()
    if (singleAction != null) {
      invokeAction(singleAction)
    }
    else {
      chooseAction(project, editor, file, actions, ::invokeAction)
    }
  }

  /**
   * @param actions list of available actions with 2 elements minimum
   * @param invokeAction consumer which invokes the selected action
   */
  protected abstract fun chooseAction(project: Project, editor: Editor, file: PsiFile, actions: List<T>, invokeAction: (T) -> Unit)
}
