// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.underModalProgress
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.command.runCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile

private typealias Target = Navigatable

object GotoDeclarationActionHandler : CodeInsightActionHandler {

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.symbol.declaration")
    val targets = try {
      underModalProgress(project, "Resolving Reference...") {
        listOfTargets()
      }
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotification("Navigation is not available here during index update")
      return
    }

    if (targets.isEmpty()) {
      if (!tryFindUsages()) {
        notifyCantGoAnywhere()
      }
    }
    else {
      val target = chooseTarget(targets) ?: return
      if (!navigateInCurrentEditor(project, editor, file, target)) {
        target.goIfCan()
      }
    }
  }

  private fun listOfTargets(): List<Target> = TODO()

  private fun tryFindUsages(): Boolean = TODO()

  private fun notifyCantGoAnywhere(): Unit = TODO()

  private fun chooseTarget(targets: List<Target>): Target? = TODO()

  private fun navigateInCurrentEditor(project: Project, editor: Editor, file: PsiFile, target: Target): Boolean {
    if (editor.isDisposed || target !is OpenFileDescriptor || target.file != file.virtualFile) return false
    runCommand {
      IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
      target.navigateIn(editor)
    }
    return true
  }

  private fun Target.goIfCan() {
    if (canNavigate()) {
      navigate(true)
    }
  }
}
