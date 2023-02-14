// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

abstract class ReferencesCodeVisionProvider : CodeVisionProviderBase() {

  override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
    GotoDeclarationAction.startFindUsages(editor, element.project, element, if (event == null) null else RelativePoint(event))
  }

  override val name: String
    get() = CodeInsightBundle.message("settings.inlay.hints.usages")
  override val groupId: String
    get() = PlatformCodeVisionIds.USAGES.key
}