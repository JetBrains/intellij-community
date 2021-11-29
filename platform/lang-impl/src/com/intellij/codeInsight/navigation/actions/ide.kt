// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.impl.gtdTargetNavigatable
import com.intellij.ide.IdeEventQueue
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent

internal fun navigateToLookupItem(project: Project): Boolean {
  val navigatable = TargetElementUtil.getTargetElementFromLookup(project)?.let(::gtdTargetNavigatable) ?: return false
  gotoTarget(project, navigatable)
  return true
}

internal fun gotoTarget(project: Project, navigatable: Navigatable) {
  if (navigatable.canNavigate()) {
    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
    navigatable.navigate(true)
  }
}

internal fun notifyNowhereToGo(project: Project, editor: Editor, file: PsiFile, offset: Int) {
  // Disable the 'no declaration found' notification for keywords
  if (Registry.`is`("ide.gtd.show.error") && !isUnderDoubleClick() && !isKeywordUnderCaret(project, file, offset)) {
    HintManager.getInstance().showInformationHint(editor, CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
  }
}

private fun isUnderDoubleClick(): Boolean {
  val event = IdeEventQueue.getInstance().trueCurrentEvent
  return event is MouseEvent && event.clickCount == 2
}

private fun isKeywordUnderCaret(project: Project, file: PsiFile, offset: Int): Boolean {
  val elementAtCaret = file.findElementAt(offset) ?: return false
  val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(elementAtCaret.language)
  return namesValidator.isKeyword(elementAtCaret.text, project)
}
