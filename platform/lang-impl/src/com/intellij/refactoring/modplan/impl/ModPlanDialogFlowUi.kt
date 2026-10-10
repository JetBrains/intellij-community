// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.codeInspection.options.OptionContainer
import com.intellij.codeInspection.ui.OptPaneUtils
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModEditOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
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

    val usageAdapters = UsageInfo2UsageAdapter.convert(usages)
    val usageView = UsageViewManager.getInstance(project).showUsages(targets, usageAdapters, presentation(refactoring, usageAdapters))
    usageView.addPerformOperationAction(
      { proceed(false) }, commandName, RefactoringBundle.message("usageView.need.reRun"), RefactoringBundle.message("usageView.doAction"),
      false, false
    )
    usageView.setRerunAction(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        usageView.close()
        proceed(true)
      }
    })
    return true
  }

  override fun showError(project: Project, refactoring: ModPlanRefactoring) {
    CommonRefactoringUtil.showErrorHint(
      project, null, RefactoringBundle.message("cannot.perform.refactoring"), refactoring.title(), refactoring.id()
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
}