// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import org.jetbrains.annotations.ApiStatus

internal class HideAllToolWindowsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { ToolWindowManager.getInstance(it) as? ToolWindowManagerImpl }?.let {
      val idsToHide = getIDsToHide(it)
      val window = e.getData(EditorWindow.DATA_KEY)
      if (window != null && window.owner.isFloating) return

      if (idsToHide.isNotEmpty()) {
        val layout = it.layout.copy()
        it.clearSideStack()
        //it.activateEditorComponent();
        for (id in idsToHide) {
          it.hideToolWindow(id, false, true, ToolWindowEventSource.HideAllWindowsAction)
        }
        it.layoutToRestoreLater = layout
        it.activateEditorComponent()
      }
      else {
        val restoredLayout = it.layoutToRestoreLater
        if (restoredLayout != null) {
          it.layoutToRestoreLater = null
          it.layout = restoredLayout
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @ApiStatus.Internal
    fun getIDsToHide(toolWindowManager: ToolWindowManagerEx): Set<String> {
      val set = HashSet<String>()
      toolWindowManager.toolWindowIds.forEach {
        if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, it)) {
          set.add(it)
        }
      }
      return set
    }
  }

  override fun update(event: AnActionEvent) {
    with(event.presentation) {
      isEnabled = false

      event.project?.let { ToolWindowManager.getInstance(it) as? ToolWindowManagerEx }?.let {
        val window = event.getData(EditorWindow.DATA_KEY)
        if (window == null || !window.owner.isFloating) {
          if (getIDsToHide(it).isNotEmpty()) {
            isEnabled = true
            putClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY, false)
            text = IdeBundle.message("action.hide.all.windows")
            return
          }

          if (it.layoutToRestoreLater != null) {
            isEnabled = true
            text = IdeBundle.message("action.restore.windows")
            putClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY, true)
            return
          }
        }
      }
    }
  }
}