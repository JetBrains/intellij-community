// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.find.actions

import com.intellij.CommonBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.targetPopupPresentation
import com.intellij.find.FindBundle
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.impl.searchTargets
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.navigation.chooseTargetPopup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import org.jetbrains.annotations.ApiStatus

/* This file contains weird logic so Symbols will work with PsiElements and UsageTargets. */

internal interface UsageVariantHandler {
  fun handleTarget(target: SearchTarget)
  fun handlePsi(element: PsiElement)
}

internal fun findShowUsages(project: Project,
                            dataContext: DataContext,
                            allTargets: List<TargetVariant>,
                            @PopupTitle popupTitle: String,
                            handler: UsageVariantHandler) {
  when (allTargets.size) {
    0 -> {
      val editor = dataContext.getData(CommonDataKeys.EDITOR)
      val message = FindBundle.message("find.no.usages.at.cursor.error")
      if (editor == null) {
        Messages.showMessageDialog(project, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon())
      }
      else {
        HintManager.getInstance().showErrorHint(editor, message)
      }
    }
    1 -> {
      allTargets.single().handle(handler)
    }
    else -> {
      chooseTargetPopup(popupTitle, allTargets, TargetVariant::presentation) {
        it.handle(handler)
      }.showInBestPositionFor(dataContext)
    }
  }
}

/**
 * @see FindUsagesAction.findUsageTargetUsages
 * @see com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.doChooseAmbiguousTarget
 */
internal fun allTargets(dataContext: DataContext): List<TargetVariant> {
  val allTargets = ArrayList<TargetVariant>()
  searchTargets(dataContext).mapTo(allTargets, ::SearchTargetVariant)
  val usageTargets: Array<out UsageTarget>? = dataContext.getData(UsageView.USAGE_TARGETS_KEY)
  if (usageTargets == null) {
    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    if (editor != null) {
      val offset = editor.caretModel.offset
      val reference = TargetElementUtil.findReference(editor, offset)
      if (reference != null) {
        TargetElementUtil.getInstance().getTargetCandidates(reference).mapTo(allTargets, ::PsiTargetVariant)
      }
    }
  }
  else {
    val target: UsageTarget = usageTargets[0]
    allTargets += if (target is PsiElement2UsageTargetAdapter) {
      PsiTargetVariant(target.element)
    }
    else {
      CustomTargetVariant(target)
    }
  }
  return allTargets
}

internal fun searchTargets(dataContext: DataContext): List<SearchTarget> {
  val file = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return emptyList()
  val offset: Int = dataContext.getData(CommonDataKeys.CARET)?.offset ?: return emptyList()
  return searchTargets(file, offset)
}

internal sealed class TargetVariant {
  abstract val presentation: TargetPopupPresentation
  abstract fun handle(handler: UsageVariantHandler)
}

internal class SearchTargetVariant(private val target: SearchTarget) : TargetVariant() {
  override val presentation: TargetPopupPresentation get() = target.presentation
  override fun handle(handler: UsageVariantHandler): Unit = handler.handleTarget(target)
}

internal class PsiTargetVariant(private val element: PsiElement) : TargetVariant() {
  override val presentation: TargetPopupPresentation get() = targetPopupPresentation(element)
  override fun handle(handler: UsageVariantHandler): Unit = handler.handlePsi(element)
}

private class CustomTargetVariant(private val target: UsageTarget) : TargetVariant() {
  override val presentation: TargetPopupPresentation get() = targetPopupPresentation(target.presentation!!)
  override fun handle(handler: UsageVariantHandler): Unit = target.findUsages()
}
