// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup

internal object GotoDeclarationOnlyHandler2 : CodeInsightActionHandler {

  override fun startInWriteAction(): Boolean = false

  private fun gotoDeclaration(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDActionData? {
    return fromGTDProviders(project, editor, offset)
           ?: gotoDeclaration(file, offset)
  }

  fun getCtrlMouseInfo(editor: Editor, file: PsiFile, offset: Int): CtrlMouseInfo? {
    return gotoDeclaration(file.project, editor, file, offset)?.ctrlMouseInfo()
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration.only")
    if (navigateToLookupItem(project)) {
      return
    }
    if (EditorUtil.isCaretInVirtualSpace(editor)) {
      return
    }

    val offset = editor.caretModel.offset
    val actionResult: GTDActionResult? = try {
      underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference")) {
        gotoDeclaration(project, editor, file, offset)?.result()
      }
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotification(
        CodeInsightBundle.message("popup.content.navigation.not.available.during.index.update"))
      return
    }

    if (actionResult == null) {
      notifyNowhereToGo(project, editor, file, offset)
    }
    else {
      gotoDeclaration(project, editor, actionResult)
    }
  }

  internal fun gotoDeclaration(project: Project, editor: Editor, actionResult: GTDActionResult) {
    when (actionResult) {
      is GTDActionResult.SingleTarget -> {
        recordAndNavigate(
          project, actionResult.navigatable(), GotoDeclarationAction.getCurrentEventData(), actionResult.navigationProvider
        )
      }
      is GTDActionResult.MultipleTargets -> {
        // obtain event data before showing the popup,
        // because showing the popup will finish the GotoDeclarationAction#actionPerformed and clear the data
        val eventData: List<EventPair<*>> = GotoDeclarationAction.getCurrentEventData()
        val popup = createTargetPopup(
          CodeInsightBundle.message("declaration.navigation.title"),
          actionResult.targets, GTDTarget::presentation
        ) { (navigatable, _, navigationProvider) ->
          recordAndNavigate(project, navigatable(), eventData, navigationProvider)
        }
        popup.showInBestPositionFor(editor)
      }
    }
  }

  private fun recordAndNavigate(
    project: Project,
    navigatable: Navigatable,
    eventData: List<EventPair<*>>,
    navigationProvider: Any?
  ) {
    if (navigationProvider != null) {
      GTDUCollector.recordNavigated(eventData, navigationProvider.javaClass)
    }
    gotoTarget(project, navigatable)
  }
}
