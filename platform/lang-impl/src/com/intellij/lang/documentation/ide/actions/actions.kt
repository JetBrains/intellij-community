// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.DocumentationBrowserFacade
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.lang.documentation.ide.ui.DocumentationToolWindowUI
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

@JvmField
val DOCUMENTATION_TARGETS: DataKey<List<DocumentationTarget>> = DataKey.create("documentation.targets")

@JvmField
val DOCUMENTATION_BROWSER: DataKey<DocumentationBrowserFacade> = DataKey.create("documentation.browser")

internal val DOCUMENTATION_POPUP: DataKey<JBPopup> = DataKey.create("documentation.popup")

internal const val PRIMARY_GROUP_ID: String = "Documentation.PrimaryGroup"
internal const val TOGGLE_SHOW_IN_POPUP_ACTION_ID: String = "Documentation.ToggleShowInPopup"
internal const val TOGGLE_AUTO_SHOW_ACTION_ID: String = "Documentation.ToggleAutoShow"
internal const val TOGGLE_AUTO_UPDATE_ACTION_ID: String = "Documentation.ToggleAutoUpdate"

internal fun primaryActions(): List<AnAction> = groupActions(PRIMARY_GROUP_ID)
internal fun navigationActions(): List<AnAction> = groupActions("Documentation.Navigation")
private fun groupActions(groupId: String) = listOf(*requireNotNull(ActionUtil.getActionGroup(groupId)).getChildren(null))

internal fun registerBackForwardActions(component: JComponent) {
  EmptyAction.registerWithShortcutSet("Documentation.Back", CustomShortcutSet(
    KeyboardShortcut.fromString(if (ScreenReader.isActive()) "alt LEFT" else "LEFT"),
    KeymapUtil.parseMouseShortcut("button4"),
  ), component)
  EmptyAction.registerWithShortcutSet("Documentation.Forward", CustomShortcutSet(
    KeyboardShortcut.fromString(if (ScreenReader.isActive()) "alt RIGHT" else "RIGHT"),
    KeymapUtil.parseMouseShortcut("button5")
  ), component)
}

@VisibleForTesting
fun documentationTargets(dc: DataContext): List<DocumentationTarget> {
  try {
    return documentationTargetsInner(dc)
  }
  catch (ignored: IndexNotReadyException) {
    return emptyList()
  }
}

private fun documentationTargetsInner(dc: DataContext): List<DocumentationTarget> {
  val contextTargets = dc.getData(DOCUMENTATION_TARGETS)
  if (contextTargets != null) {
    return contextTargets
  }
  val project = dc.getData(CommonDataKeys.PROJECT)
                ?: return emptyList()
  val editor = dc.getData(CommonDataKeys.EDITOR)
  if (editor != null) {
    val editorTargets = targetsFromEditor(project, editor, editor.caretModel.offset)
    if (editorTargets != null) {
      return editorTargets
    }
  }
  val symbols = dc.getData(CommonDataKeys.SYMBOLS)
  if (!symbols.isNullOrEmpty()) {
    val symbolTargets = symbolDocumentationTargets(project, symbols)
    if (symbolTargets.isNotEmpty()) {
      return symbolTargets
    }
  }
  val targetElement = dc.getData(CommonDataKeys.PSI_ELEMENT)
  if (targetElement != null) {
    return listOf(psiDocumentationTarget(targetElement) ?: PsiElementDocumentationTarget(project, targetElement))
  }
  return emptyList()
}

@ApiStatus.Internal
fun targetsFromEditor(project: Project, editor: Editor, offset: Int): List<DocumentationTarget>? {
  val file = PsiUtilBase.getPsiFileInEditor(editor, project)
             ?: return null
  val ideTargetProvider = IdeDocumentationTargetProvider.getInstance(project)
  val lookup = LookupManager.getActiveLookup(editor)
  if (lookup != null) {
    val lookupElement = lookup.currentItem
                        ?: return null
    val target = ideTargetProvider.documentationTarget(editor, file, lookupElement)
                 ?: return null
    return listOf(target)
  }
  return ideTargetProvider.documentationTargets(editor, file, offset).takeIf {
    it.isNotEmpty()
  }
}

internal fun documentationHistory(dc: DataContext): DocumentationHistory? {
  return documentationBrowser(dc)?.history
}

internal fun documentationBrowser(dc: DataContext): DocumentationBrowser? {
  val browser = dc.getData(DOCUMENTATION_BROWSER)
  if (browser != null) {
    return browser as DocumentationBrowser
  }
  return documentationToolWindowUI(dc)?.browser
}

internal fun documentationToolWindowUI(dc: DataContext): DocumentationToolWindowUI? {
  val toolWindow = dc.getData(PlatformDataKeys.TOOL_WINDOW)
                   ?: return null
  if (toolWindow.id != DocumentationToolWindowManager.TOOL_WINDOW_ID) {
    return null
  }
  val component = dc.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
  val content = if (component is BaseLabel) {
    component.content
  }
  else {
    toolWindow.contentManager.selectedContent
  }
  return content?.toolWindowUI
}
