// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.find.actions

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.find.FindBundle
import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.createTargetPopup
import com.intellij.usages.UsageTarget
import org.jetbrains.annotations.ApiStatus

/* This file contains weird logic so Symbols will work with PsiElements and UsageTargets. */

internal fun allTargets(dataContext: DataContext): List<TargetVariant> {
  return targetVariants(dataContext)
}

internal interface UsageVariantHandler {
  fun handleTarget(target: SearchTarget)
  fun handlePsi(element: PsiElement)
}

internal fun findShowUsages(project: Project,
                            editor: Editor?,
                            popupPosition: RelativePoint,
                            allTargets: List<TargetVariant>,
                            @PopupTitle popupTitle: String,
                            handler: UsageVariantHandler) {
  when (allTargets.size) {
    0 -> {
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
      createTargetPopup(popupTitle, allTargets, TargetVariant::presentation) {
        it.handle(handler)
      }.show(popupPosition)
    }
  }
}

internal sealed class TargetVariant {
  abstract val presentation: TargetPresentation
  abstract fun handle(handler: UsageVariantHandler)
}

internal class SearchTargetVariant(private val target: SearchTarget) : TargetVariant() {
  override val presentation: TargetPresentation = target.presentation()
  override fun handle(handler: UsageVariantHandler): Unit = handler.handleTarget(target)
}

internal class PsiTargetVariant(private val element: PsiElement) : TargetVariant() {
  override val presentation: TargetPresentation = targetPresentation(element)
  override fun handle(handler: UsageVariantHandler): Unit = handler.handlePsi(element)
}

internal class CustomTargetVariant(private val target: UsageTarget) : TargetVariant() {
  override val presentation: TargetPresentation = targetPresentation(target.presentation!!)
  override fun handle(handler: UsageVariantHandler): Unit = target.findUsages()
}
