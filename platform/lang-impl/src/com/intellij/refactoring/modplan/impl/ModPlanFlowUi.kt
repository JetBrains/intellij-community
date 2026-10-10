// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTarget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus

/**
 * Everything that [ModPlanFlow] shows or asks, so that a client that has no dialogs, or a test, replaces it as a service. In unit tests
 * the service is [ModPlanTestFlowUi].
 */
@ApiStatus.Internal
interface ModPlanFlowUi {
  /**
   * Asks [question] as [ModPlanQuestion.askCommand] does: a form in a dialog, a custom question with its own UI. The dialog starts with the
   * values that the storage of the question remembers, and the storage remembers the answers after OK.
   *
   * @return the answers on top of [initialValues]; null if the user cancelled
   */
  @RequiresEdt
  fun ask(project: Project, title: @NlsContexts.DialogTitle String, question: ModPlanQuestion, initialValues: ModPlanOptionValues): ModPlanOptionValues?

  /**
   * @param conflicts the messages of the conflicts by the element they are about
   * @return true if the user goes on in spite of the conflicts
   */
  @RequiresEdt
  fun confirmConflicts(project: Project, conflicts: MultiMap<PsiElement, String>): Boolean

  /**
   * Shows the non-code [usages] that the refactoring leaves as they are. The preview is not modal: this method returns at once, and the
   * user's choice comes later through [proceed]: false to go on and write, true to plan again and show the preview again.
   *
   * @param commandName the name of the action that goes on
   * @return false if there is nothing to show
   */
  @RequiresEdt
  fun previewNonCodeUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    commandName: @NlsContexts.Command String,
    targets: Array<out UsageTarget>,
    usages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean) -> Unit,
  ): Boolean

  /** Tells the user that [refactoring] failed. */
  @RequiresEdt
  fun showError(project: Project, refactoring: ModPlanRefactoring)

  companion object {
    @JvmStatic
    fun getInstance(): ModPlanFlowUi = ApplicationManager.getApplication().service()
  }
}
