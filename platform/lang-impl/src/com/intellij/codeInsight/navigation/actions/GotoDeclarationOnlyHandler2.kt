// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
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

  fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
    return gotoDeclaration(file.project, editor, file, offset)?.ctrlMouseData()
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
    val actionResult: NavigationActionResult? = try {
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

  internal fun gotoDeclaration(project: Project, editor: Editor, actionResult: NavigationActionResult) {
    // obtain event data before showing the popup,
    // because showing the popup will finish the GotoDeclarationAction#actionPerformed and clear the data
    val eventData: List<EventPair<*>> = GotoDeclarationAction.getCurrentEventData()
    when (actionResult) {
      is SingleTarget -> {
        actionResult.navigationProvider?.let {
          GTDUCollector.recordNavigated(eventData, it.javaClass)
        }
        navigateRequest(project, actionResult.request)
      }
      is MultipleTargets -> {
        val popup = createTargetPopup(
          CodeInsightBundle.message("declaration.navigation.title"),
          actionResult.targets, LazyTargetWithPresentation::presentation
        ) { (requestor, _, navigationProvider) ->
          navigationProvider?.let {
            GTDUCollector.recordNavigated(eventData, navigationProvider.javaClass)
          }
          navigateRequestLazy(project, requestor)
        }
        popup.showInBestPositionFor(editor)
      }
    }
  }
}
