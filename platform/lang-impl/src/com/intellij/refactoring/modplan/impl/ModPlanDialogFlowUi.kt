// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.codeInspection.options.OptionContainer
import com.intellij.codeInspection.ui.OptPaneUtils
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModEditOptions
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiFileRange
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

/** The dialogs of the legacy refactorings: the options dialog, the conflicts dialog and the usage view. */
@ApiStatus.Internal
class ModPlanDialogFlowUi : ModPlanFlowUi {
  override fun ask(project: Project, title: @NlsContexts.DialogTitle String, question: ModPlanQuestion, initialValues: ModPlanOptionValues): ModPlanOptionValues? {
    val storage = question.storage()
    val loadedValues = storage?.load(initialValues) ?: initialValues
    // askCommand gives the dialog as a ModEditOptions. Its next command only keeps the answers.
    var finalAnswers: ModPlanOptionValues? = null
    val command = ModPlanQuestion.askCommand(title, question, loadedValues) {
      finalAnswers = it
      ModCommand.nop()
    }
    // The edit shows the dialog; null stands for the cancel of the dialog.
    if (command is ModEditOptions<*>) {
      edit(project, command) ?: return null
    }
    val result = finalAnswers ?: return null
    storage?.store(result)
    return result
  }

  override fun confirmConflicts(project: Project, conflicts: MultiMap<PsiElement, String>): Boolean {
    return ConflictsDialog(project, conflicts).showAndGet()
  }

  override fun previewNonCodeUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    commandName: @NlsContexts.Command String,
    targets: Array<out UsageTarget>,
    usages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean) -> Unit,
  ): Boolean {
    if (!UsageViewUtil.reportNonRegularUsages(usages, project)) return false
    showUsages(project, refactoring, commandName, targets, usages) { previewAgain, _ -> proceed(previewAgain) }
    return true
  }

  override fun previewUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    targets: Array<out UsageTarget>,
    plan: ModPlan.AcceptedPlan,
    nonCodeUsages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean, excludedUsages: Set<SmartPsiFileRange>) -> Unit,
  ): Boolean {
    val nonCodeLocations = plan.conflicts.changedNonCodeUsages().mapNotNullTo(HashSet()) { it.location }
    val changedUsages = runReadActionBlocking { usagesAt(plan.usages, nonCodeLocations) }
    val allUsages = changedUsages.keys.toTypedArray() + nonCodeUsages
    if (allUsages.isEmpty()) return false
    UsageViewUtil.reportNonRegularUsages(allUsages, project)
    // The non-code usages are not changed, so an excluded one changes nothing.
    showUsages(project, refactoring, plan.execution.commandName, targets, allUsages) { previewAgain, excludedUsages ->
      proceed(previewAgain, excludedUsages.mapNotNullTo(HashSet()) { changedUsages[it] })
    }
    return true
  }

  /** Shows [usages] in the usage view, where the user may exclude some; [proceed] gets the excluded ones when the user goes on. */
  private fun showUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    commandName: @NlsContexts.Command String,
    targets: Array<out UsageTarget>,
    usages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean, excludedUsages: Set<UsageInfo>) -> Unit,
  ) {
    val usageAdapters = UsageInfo2UsageAdapter.convert(usages)
    val usageView = UsageViewManager.getInstance(project).showUsages(targets, usageAdapters, presentation(refactoring, usageAdapters))
    usageView.addPerformOperationAction(
      {
        val notExcludedUsages = UsageViewUtil.getNotExcludedUsageInfos(usageView)
        proceed(false, usages.filterNotTo(HashSet()) { it in notExcludedUsages })
      },
      commandName, RefactoringBundle.message("usageView.need.reRun"), RefactoringBundle.message("usageView.doAction"), false, false
    )
    usageView.setRerunAction(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        usageView.close()
        proceed(true, emptySet())
      }
    })
  }

  override fun showError(project: Project, refactoring: ModPlanRefactoring, message: @NlsContexts.DialogMessage String?) {
    CommonRefactoringUtil.showErrorHint(
      project, null, RefactoringBundle.getCannotRefactorMessage(message), refactoring.title(), refactoring.id()
    )
  }

  /**
   * Shows the dialog of [options], or the UI of its custom question, see [OptPaneUtils.editOptions].
   *
   * @return the next command, which asks the next question or gives the answers; null if the user cancelled
   */
  private fun <T : OptionContainer> edit(project: Project, options: ModEditOptions<T>): ModCommand? {
    val container = options.containerSupplier().get()
    var accepted = false
    OptPaneUtils.editOptions(project, container, options.title()) { accepted = true }
    return if (accepted) options.nextCommand().apply(container) else null
  }

  private fun presentation(refactoring: ModPlanRefactoring, usages: Array<out Usage>): UsageViewPresentation {
    val files = usages.mapNotNullTo(HashSet()) { (it as? PsiElementUsage)?.element?.containingFile }
    return UsageViewPresentation().apply {
      tabText = RefactoringBundle.message("usageView.tabText")
      targetsNodeText = refactoring.targetsNodeText()
      isShowReadOnlyStatusAsRed = true
      isShowCancelButton = true
      codeUsagesString = UsageViewBundle.message(
        "usage.view.results.node.prefix", UsageViewBundle.message("usage.view.results.node.code"),
        RefactoringBundle.message("references.in.code", UsageViewBundle.getReferencesString(0, 0))
      )
      nonCodeUsagesString = UsageViewBundle.message(
        "usage.view.results.node.prefix", UsageViewBundle.message("usage.view.results.node.non.code"),
        refactoring.nonCodeUsagesHeader(usages.size, files.size)
      )
    }
  }

  /**
   * @param nonCodeLocations the locations of [locations] whose usages are non-code usages
   * @return the usages of the usage view at [locations], with their locations; a location whose file is gone has none
   */
  @RequiresReadLock
  private fun usagesAt(locations: List<SmartPsiFileRange>, nonCodeLocations: Set<SmartPsiFileRange>): Map<UsageInfo, SmartPsiFileRange> {
    val usages = LinkedHashMap<UsageInfo, SmartPsiFileRange>()
    for (location in locations) {
      val file = location.element ?: continue
      val range = location.range
      val nonCode = location in nonCodeLocations
      usages[if (range == null) UsageInfo(file, nonCode) else UsageInfo(file, range.startOffset, range.endOffset, nonCode)] = location
    }
    return usages
  }
}

/**
 * The UI of the unit-test mode, as the legacy refactorings behave in it: nothing is asked, conflicts are thrown unless the test ignores
 * them, and no preview is shown. The rest is the real UI, which also fails in a test.
 */
@VisibleForTesting
@ApiStatus.Internal
open class ModPlanTestFlowUi : ModPlanFlowUi by ModPlanDialogFlowUi() {
  override fun ask(project: Project, title: @NlsContexts.DialogTitle String, question: ModPlanQuestion, initialValues: ModPlanOptionValues): ModPlanOptionValues? = initialValues

  override fun confirmConflicts(project: Project, conflicts: MultiMap<PsiElement, String>): Boolean {
    if (!BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
      throw BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values())
    }
    return true
  }

  override fun previewNonCodeUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    commandName: @NlsContexts.Command String,
    targets: Array<out UsageTarget>,
    usages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean) -> Unit,
  ): Boolean = false

  override fun previewUsages(
    project: Project,
    refactoring: ModPlanRefactoring,
    targets: Array<out UsageTarget>,
    plan: ModPlan.AcceptedPlan,
    nonCodeUsages: Array<UsageInfo>,
    proceed: (previewAgain: Boolean, excludedUsages: Set<SmartPsiFileRange>) -> Unit,
  ): Boolean = false
}