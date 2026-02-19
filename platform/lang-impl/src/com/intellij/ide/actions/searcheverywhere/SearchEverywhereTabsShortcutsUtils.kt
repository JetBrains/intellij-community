// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SearchEverywhereTabsShortcutsUtils {
  fun createShortcutsMap(): MutableMap<String, String> {
    val res: MutableMap<String, String> = HashMap()

    res[SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID] = LangBundle.message("double.shift")
    addShortcut(res, "ClassSearchEverywhereContributor", "GotoClass")
    addShortcut(res, "FileSearchEverywhereContributor", "GotoFile")
    addShortcut(res, "SymbolSearchEverywhereContributor", "GotoSymbol")
    addShortcut(res, "ActionSearchEverywhereContributor", "GotoAction")
    addShortcut(res, "DbSETablesContributor", "GotoDatabaseObject")
    addShortcut(res, "TextSearchContributor", "TextSearchAction")
    addShortcut(res, "UrlSearchEverywhereContributor", "GotoUrlAction")

    return res
  }

  private fun addShortcut(map: MutableMap<String, String>, tabId: String, actionID: String) {
    val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionID)
    if (shortcut != null) map[tabId] = KeymapUtil.getShortcutText(shortcut)
  }
}