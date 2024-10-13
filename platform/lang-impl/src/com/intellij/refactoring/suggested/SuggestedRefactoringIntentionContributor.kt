// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.SuggestedRefactoringState.ErrorLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

internal val REFACTORING_DATA_KEY: Key<SuggestedRefactoringData> = Key.create<SuggestedRefactoringData>("suggested.refactoring.data")

@ApiStatus.Internal
class SuggestedRefactoringIntentionContributor : IntentionMenuContributor {
  private val icon = AllIcons.Actions.SuggestedRefactoringBulb

  override fun collectActions(
    hostEditor: Editor,
    hostFile: PsiFile,
    intentions: ShowIntentionsPass.IntentionsInfo,
    passIdToShowIntentionsFor: Int,
    offset: Int
  ) {
    val intention = suggestRefactoringIntention(hostFile, offset)

    if (intention == null) {
      hostFile.removeUserData(REFACTORING_DATA_KEY)
      return
    }

    // we add it into 'errorFixesToShow' if it's not empty to always be at the top of the list
    // we don't add into it if it's empty to keep the color of the bulb
    val collectionToAdd = intentions.inspectionFixesToShow
    collectionToAdd.add(HighlightInfo.IntentionActionDescriptor(intention, null, null, icon, null, null, null))
  }

  private fun suggestRefactoringIntention(hostFile: PsiFile, offset: Int): MyIntention? {
    val project = hostFile.project
    if (LightEdit.owns(project)) return null
    val refactoringProvider = SuggestedRefactoringProviderImpl.getInstance(project)
    var state = refactoringProvider.state
    if (state == null) return null

    val anchor = state.anchor
    if (!anchor.isValid || state.errorLevel == ErrorLevel.INCONSISTENT) return null
    if (hostFile != anchor.containingFile) return null

    val refactoringSupport = state.refactoringSupport

    if (refactoringSupport.availability.shouldSuppressRefactoringForDeclaration(state)) {
      // additional checks showed that the initial declaration was unsuitable for refactoring
      ApplicationManager.getApplication().invokeLater {
        refactoringProvider.suppressForCurrentDeclaration()
      }
      return null
    }

    if (state.errorLevel != ErrorLevel.NO_ERRORS) return null

    state = refactoringSupport.availability.refineSignaturesWithResolve(state)

    if (state.errorLevel == ErrorLevel.SYNTAX_ERROR || state.oldSignature == state.newSignature) {
      val document = PsiDocumentManager.getInstance(project).getDocument(hostFile)!!
      val modificationStamp = document.modificationStamp
      ApplicationManager.getApplication().invokeLater {
        if (document.modificationStamp == modificationStamp) {
          refactoringProvider.availabilityIndicator.clear()
        }
      }
      return null
    }

    val refactoringData = refactoringSupport.availability.detectAvailableRefactoring(state)

    // update availability indicator with more precise state that takes into account resolve
    refactoringProvider.availabilityIndicator.update(anchor, refactoringData, refactoringSupport)

    if (refactoringData != null) hostFile.putUserData(REFACTORING_DATA_KEY, refactoringData)

    val range = when (refactoringData) {
      is SuggestedRenameData -> refactoringSupport.nameRange(refactoringData.declaration)!!
      is SuggestedChangeSignatureData -> refactoringSupport.changeSignatureAvailabilityRange(anchor)!!
      else -> return null
    }

    if (!range.containsOffset(offset)) return null

    SuggestedRefactoringFeatureUsage.refactoringSuggested(refactoringData, state)
    val text = refactoringData.getIntentionText()

    return MyIntention(text, showReviewBalloon = refactoringData is SuggestedChangeSignatureData)
  }

  private class MyIntention(
    @IntentionName private val text: String,
    private val showReviewBalloon: Boolean
  ) : IntentionAction, PriorityAction {
    override fun getPriority() = PriorityAction.Priority.HIGH

    @NonNls
    override fun getFamilyName() = "Suggested Refactoring"

    override fun getText() = text

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile?) = true

    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
      performSuggestedRefactoring(project, editor, null, null, showReviewBalloon, ActionPlaces.INTENTION_MENU)
    }
  }
}