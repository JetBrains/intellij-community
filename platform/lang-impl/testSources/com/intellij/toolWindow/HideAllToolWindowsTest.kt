// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.testFramework.MapDataContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class HideAllToolWindowsTest : ToolWindowManagerTestCase() {
  fun testDontHideFloatingAndWindowedToolWindows() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        val extensions = ToolWindowEP.EP_NAME.extensionList
        val types: MutableMap<String, ToolWindowType> = HashMap()
        val cycle = listOf(*ToolWindowType.values())
        for (i in extensions.indices) {
          val extension = extensions[i]
          val id = extension.id
          if (id == "Structure" || id == ToolWindowId.FAVORITES_VIEW || id == ToolWindowId.BOOKMARKS || id == "Ant") {
            continue
          }
          manager!!.initToolWindow(extension)
          // if not applicable, then will be not registered
          if (!manager!!.isToolWindowRegistered(id)) {
            continue
          }
          manager!!.showToolWindow(id)
          val type = cycle[i % cycle.size]
          types[id] = type
          manager!!.setToolWindowType(id, type)
        }
        val context = MapDataContext()
        context.put(CommonDataKeys.PROJECT, project)
        val visibleIds: MutableSet<String> = HashSet()
        for (id in types.keys) {
          if (manager!!.getToolWindow(id)!!.isVisible) {
            visibleIds.add(id)
          }
        }
        ActionManager.getInstance().getAction("HideAllWindows").actionPerformed(AnActionEvent.createFromDataContext("", null, context))
        for (id in visibleIds) {
          val window = manager!!.getToolWindow(id)
          assertEquals(id + ":" + window!!.type,
                       types[id] == ToolWindowType.FLOATING || types[id] == ToolWindowType.WINDOWED,
                       window.isVisible)
        }
      }
    }
  }
}