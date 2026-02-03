// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.impl.NavigationRequestor
import com.intellij.codeInsight.navigation.impl.gtdTargetNavigatable
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.navigateBlocking
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.MouseEvent

internal fun navigateToLookupItem(project: Project, editor: Editor): Boolean {
  val activeLookup: Lookup? = LookupManager.getInstance(project).activeLookup
  if (activeLookup == null) {
    return false
  }
  val currentItem = activeLookup.currentItem
  navigateRequestLazy(project, {
    TargetElementUtil.targetElementFromLookupElement(currentItem)
      ?.gtdTargetNavigatable()
      ?.navigationRequest()
  }, editor)
  return true
}

/**
 * Obtains a [NavigationRequest] instance from [requestor] on a background thread, and calls [navigateRequest].
 */
internal fun navigateRequestLazy(project: Project, requestor: NavigationRequestor, editor: Editor) {
  EDT.assertIsEdt()
  @Suppress("DialogTitleCapitalization")
  val request = underModalProgress(project, ActionsBundle.actionText("GotoDeclarationOnly")) {
    requestor.navigationRequest()
  }
  if (request != null) {
    val dataContext = editor.component.let { DataManager.getInstance().getDataContext(it) }
    navigateRequest(project, request, dataContext = dataContext)
  }
}

@Internal
@RequiresEdt
@JvmOverloads
fun navigateRequest(project: Project, request: NavigationRequest, dataContext: DataContext? = null) {
  EDT.assertIsEdt()
  IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
  navigateBlocking(project, request, NavigationOptions.requestFocus(), dataContext)
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
