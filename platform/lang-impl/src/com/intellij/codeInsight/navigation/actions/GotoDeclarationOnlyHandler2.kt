// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.find.actions.EditorToPsiMethod
import com.intellij.find.actions.addEdgeToJourney
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class GotoDeclarationOnlyHandler2(private val reporter: GotoDeclarationReporter?) : CodeInsightActionHandler {

  companion object {

    private fun gotoDeclaration(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDActionData? {
      return fromGTDProviders(project, editor, offset)
             ?: gotoDeclaration(file, offset)
    }

    fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
      return gotoDeclaration(file.project, editor, file, offset)?.ctrlMouseData()
    }

    internal fun gotoDeclaration(
      project: Project,
      editor: Editor,
      actionResult: NavigationActionResult,
      reporter: GotoDeclarationReporter?
    ) {
      // obtain event data before showing the popup,
      // because showing the popup will finish the GotoDeclarationAction#actionPerformed and clear the data
      val eventData: List<EventPair<*>> = GotoDeclarationAction.getCurrentEventData()

      fun getPsiElement(navigationRequest: SourceNavigationRequest): PsiElement? {
        val psiFile = navigationRequest.file.findPsiFile(project)!!
        val offset = navigationRequest.offsetMarker?.startOffset ?: return psiFile
        val result: PsiElement = psiFile.findElementAt(offset) ?: return psiFile
        return result
      }

      fun addToDiagram(navigationRequest: SourceNavigationRequest) {
        val psiMethodTo: PsiElement = getPsiElement(navigationRequest) ?: return
        val psiMethodFrom = EditorToPsiMethod(project, editor)
        // Reverse the arrow because go to declaration is many to one navigation - required for layout
        addEdgeToJourney(project, psiMethodTo, psiMethodFrom)
      }

      when (actionResult) {
        is SingleTarget -> {
          reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.SINGLE)
          actionResult.navigationProvider?.let {
            GTDUCollector.recordNavigated(eventData, it.javaClass)
          }
          navigateRequestLazy(project, actionResult.requestor)
          (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.EDT) {
            val navigationRequest = backgroundWriteAction {
              actionResult.requestor.navigationRequest()
            }
            if (navigationRequest is SourceNavigationRequest) {
              addToDiagram(navigationRequest)
            }
          }

          // Ascend the tree to find the enclosing PsiMethod
          reporter?.reportNavigatedToDeclaration(GotoDeclarationReporter.NavigationType.AUTO, actionResult.navigationProvider)
        }
        is MultipleTargets -> {
          reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.MULTIPLE)
          val popup = createTargetPopup(
            CodeInsightBundle.message("declaration.navigation.title"),
            actionResult.targets, LazyTargetWithPresentation::presentation
          ) { (requestor, _, navigationProvider) ->
            navigationProvider?.let {
              GTDUCollector.recordNavigated(eventData, navigationProvider.javaClass)
            }
            navigateRequestLazy(project, requestor)
            reporter?.reportNavigatedToDeclaration(GotoDeclarationReporter.NavigationType.FROM_POPUP, navigationProvider)
          }
          popup.showInBestPositionFor(editor)
          reporter?.reportLookupElementsShown()
        }
      }
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
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
    catch (_: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("popup.content.navigation.not.available.during.index.update"),
        DumbModeBlockedFunctionality.GotoDeclarationOnly)
      return
    }

    if (actionResult == null) {
      reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
      notifyNowhereToGo(project, editor, file, offset)
    }
    else {
      gotoDeclaration(project, editor, actionResult, reporter)
    }
  }
}
