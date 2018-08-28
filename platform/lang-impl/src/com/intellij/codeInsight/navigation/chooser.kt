// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.createTargetMainRenderer
import com.intellij.ide.ui.createTargetRenderer
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.list.SearchAwareRenderer
import kotlin.coroutines.experimental.suspendCoroutine

internal suspend fun chooseTarget(project: Project, editor: Editor, targets: List<NavigationTarget>): NavigationTarget {
  targets.singleOrNull()?.let {
    return it
  }
  val renderer = createRenderer(project)
  return suspendCoroutine { cont ->
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(targets)
      .setRenderer(renderer)
      .setNamerForFiltering(renderer::getItemName)
      .setFont(EditorUtil.getEditorFont())
      .setTitle(CodeInsightBundle.message("declaration.navigation.title"))
      .setItemChosenCallback(cont::resume)
      .withHintUpdateSupply()
      .createPopup()
      .showInBestPositionFor(editor)
  }
}

private fun createRenderer(project: Project): SearchAwareRenderer<NavigationTarget> {
  return if (UISettings.instance.showIconInQuickNavigation) {
    createTargetRenderer(project, NavigationTarget::presentationIfValid)
  }
  else {
    createTargetMainRenderer(project, NavigationTarget::presentationIfValid)
  }
}

private val NavigationTarget.presentationIfValid: TargetPresentation? get() = if (isValid) targetPresentation else null
