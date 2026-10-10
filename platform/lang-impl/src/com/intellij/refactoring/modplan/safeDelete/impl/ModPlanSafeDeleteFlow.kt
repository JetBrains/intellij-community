// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete.impl

import com.intellij.model.SymbolPointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.impl.ModPlanFlow
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow.REGISTRY_KEY
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * The interactive safe delete on the ModPlan engine: the request of [ModPlanFlow]; the questions and the texts come from the refactoring.
 * It is on only if the registry key [REGISTRY_KEY] is, and only for elements that [ModPlanSafeDeleteRefactoring.isSupported]; the legacy
 * flow runs for everything else.
 */
@ApiStatus.Internal
object ModPlanSafeDeleteFlow {
  private const val REGISTRY_KEY = "refactoring.safeDelete.modPlan"

  @Volatile
  private var answersInTests: ((ModPlanOptionValues) -> ModPlanOptionValues)? = null

  @Volatile
  private var failsOnLegacyInTests: Boolean = false

  /**
   * A unit test asks nothing, as the legacy flow, which answers some questions in its own way there, such as all the unused callees.
   * [answers] stand for the user: they get the options of a run without questions and return them with the answers of the test.
   */
  @TestOnly
  fun answerInTests(answers: (ModPlanOptionValues) -> ModPlanOptionValues, disposable: Disposable) {
    answersInTests = answers
    Disposer.register(disposable) { answersInTests = null }
  }

  /**
   * A test that runs the data of a legacy test on the ModPlan engine fails with an [AssertionError] if the legacy flow runs instead. Else
   * the legacy flow passes the test, and the test does not show that the ModPlan engine cannot delete the element.
   */
  @TestOnly
  fun failOnLegacyInTests(disposable: Disposable) {
    failsOnLegacyInTests = true
    Disposer.register(disposable) { failsOnLegacyInTests = false }
  }

  /**
   * Runs the safe delete of [elements] if the flow handles them. The user is asked in an interactive run only: not in a unit test and not
   * if [silent].
   *
   * @param checkDelegates false deletes exactly [elements], as the legacy flow does: no related elements are searched
   * @param successRunnable runs after the write, on the EDT
   * @param afterRefactoring runs after the write, outside its command: the legacy flow runs it inside `performRefactoring`, in the write
   * action and the undo step of the deletion.
   * @return true if the flow handled the deletion, also if the user cancelled it or it failed; false if the legacy flow has to run. False
   * can also come after the questions were asked, see [ModPlanFlow]
   */
  @JvmStatic
  @RequiresEdt
  fun run(
    project: Project,
    elements: Array<PsiElement>,
    silent: Boolean,
    checkDelegates: Boolean,
    successRunnable: Runnable?,
    afterRefactoring: Runnable?,
  ): Boolean {
    if (!Registry.`is`(REGISTRY_KEY)) return false
    val ask = !ApplicationManager.getApplication().isUnitTestMode && !silent
    return start(project, elements, ask, checkDelegates, successRunnable, afterRefactoring)
  }

  /** Like [run], with [ask] chosen by the caller and without the registry key. */
  @VisibleForTesting
  @RequiresEdt
  fun start(
    project: Project,
    elements: Array<PsiElement>,
    ask: Boolean,
    checkDelegates: Boolean,
    successRunnable: Runnable?,
    afterRefactoring: Runnable?,
  ): Boolean {
    // Without questions the options are the common settings of the legacy flow; with them the dialog starts with the remembered ones.
    val optionValues = if (ask) {
      ModPlanOptionValues.EMPTY
    }
    else {
      ModPlanSettingsSearchStorage.load(ModPlanOptionValues.EMPTY).let { answersInTests?.invoke(it) ?: it }
    }
    val request = runReadActionBlocking {
      val targets = PsiTreeUtil.filterAncestors(elements).toList()
      val requestOptionValues = if (checkDelegates) { optionValues } else { optionValues.with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false) }
      ModPlanRequest(project, targets.map(SymbolPointer::of), requestOptionValues)
    }
    val result = ModPlanFlow.run(ModPlanSafeDeleteRefactoring.getInstance(), request, ask) {
      afterRefactoring?.run()
      successRunnable?.run()
    }
    if (result == ModPlanFlow.Result.LEGACY && failsOnLegacyInTests) {
      throw AssertionError("The legacy safe delete runs for ${elements.toList()}")
    }
    return result != ModPlanFlow.Result.LEGACY
  }
}
