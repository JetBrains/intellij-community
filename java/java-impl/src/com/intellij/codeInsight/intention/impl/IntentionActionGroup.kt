// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

/**
 * This action represents a group of similar actions with the ability to choose one.
 * - if all actions in the group are unavailable, this action becomes unavailable too;
 * - if exactly one action in the group is available, then this action uses its text and invokes it without choosing;
 * - otherwise [getGroupText] and [chooseAction] are used.
 *
 * @param T type of actions
 */
abstract class IntentionActionGroup<T : IntentionAction>(private val actions: List<T>) : BaseIntentionAction() {

  final override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = null

  final override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    if (editor == null || psiFile == null) return false

    val availableActions = actions.filter { it.isAvailable(project, editor, psiFile) }
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

  final override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    if (editor == null || psiFile == null) return

    val availableActions = actions.filter { it.isAvailable(project, editor, psiFile) }
    if (availableActions.isEmpty()) return

    fun invokeAction(action: IntentionAction) {
      ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, action.text, IntentionSource.CONTEXT_ACTIONS)
    }

    val singleAction = availableActions.singleOrNull()
    if (singleAction != null) {
      invokeAction(singleAction)
    }
    else {
      chooseAction(project, editor, psiFile, actions, ::invokeAction)
    }
  }

  /**
   * @param actions list of available actions with 2 elements minimum
   * @param invokeAction consumer that invokes the selected action
   */
  protected abstract fun chooseAction(project: Project, editor: Editor, file: PsiFile, actions: List<T>, invokeAction: (T) -> Unit)
}
