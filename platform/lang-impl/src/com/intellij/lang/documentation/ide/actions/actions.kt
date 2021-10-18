// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.util.ui.accessibility.ScreenReader
import javax.swing.JComponent

@JvmField
val DOCUMENTATION_TARGETS_KEY: DataKey<List<DocumentationTarget>> = DataKey.create("documentation.targets")
internal val DOCUMENTATION_BROWSER_DATA_KEY: DataKey<DocumentationBrowser> = DataKey.create("documentation.browser")
internal val DOCUMENTATION_HISTORY_DATA_KEY: DataKey<DocumentationHistory> = DataKey.create("documentation.history")
internal val DOCUMENTATION_TARGET_POINTER_KEY: DataKey<Pointer<out DocumentationTarget>> = DataKey.create("documentation.target.pointer");
internal val DOCUMENTATION_POPUP_KEY: DataKey<JBPopup> = DataKey.create("documentation.popup")

internal const val DOCUMENTATION_PRIMARY_GROUP_ID: String = "Documentation.PrimaryGroup"
internal const val DOCUMENTATION_VIEW_EXTERNAL_ACTION_ID: String = "Documentation.ViewExternal"
internal const val TOGGLE_SHOW_IN_POPUP_ACTION_ID: String = "Documentation.ToggleShowInPopup"
internal const val TURN_OFF_AUTO_UPDATE_ACTION_ID: String = "Documentation.TurnOffAutoUpdate"

internal fun primaryActions(): List<AnAction> = groupActions(DOCUMENTATION_PRIMARY_GROUP_ID)
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
