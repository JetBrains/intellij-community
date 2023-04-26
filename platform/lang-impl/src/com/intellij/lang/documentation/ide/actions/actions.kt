// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.ide.documentation.DOCUMENTATION_BROWSER
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

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

class DocumentationTargetsDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): List<DocumentationTarget>? {
    return ContainerUtil.nullize(documentationTargetsInner(dataProvider))
  }
}

private fun documentationTargetsInner(dataProvider: DataProvider): List<DocumentationTarget> {
  val project = CommonDataKeys.PROJECT.getData(dataProvider) ?: return emptyList()
  val editor = CommonDataKeys.EDITOR.getData(dataProvider)
  if (editor != null) {
    val editorTargets = targetsFromEditor(project, editor, editor.caretModel.offset)
    if (editorTargets != null) {
      return editorTargets
    }
  }
  val symbols = CommonDataKeys.SYMBOLS.getData(dataProvider)
  if (!symbols.isNullOrEmpty()) {
    val symbolTargets = symbolDocumentationTargets(project, symbols)
    if (symbolTargets.isNotEmpty()) {
      return symbolTargets
    }
  }
  val targetElement = CommonDataKeys.PSI_ELEMENT.getData(dataProvider)
  if (targetElement != null) {
    return listOf(psiDocumentationTarget(targetElement, null))
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
  return ContainerUtil.nullize(ideTargetProvider.documentationTargets(editor, file, offset))
}

internal fun documentationHistory(dc: DataContext): DocumentationHistory? {
  return documentationBrowser(dc)?.history
}

internal fun documentationBrowser(dc: DataContext): DocumentationBrowser? {
  return dc.getData(DOCUMENTATION_BROWSER) as? DocumentationBrowser
}
