// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable
import java.awt.Component
import java.awt.event.MouseEvent

internal class HideAllToolWindowsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(e.project ?: return) as ToolWindowManagerImpl
    val idsToHide = getIDsToHide(toolWindowManager)
    val window = e.getData(EditorWindow.DATA_KEY)
    val floatingEditor = (window != null && window.owner.isFloating)
    var maximizeEditor = null as Boolean?
    if (idsToHide.isNotEmpty() && !floatingEditor) maximizeEditor = true

    val splittersToMaximize = getSplittersToMaximize(e)
    if (splittersToMaximize.isNotEmpty()) {
      maximizeEditor = true
      splittersToMaximize.forEach { it.first.proportion = if (it.second) it.first.maximumProportion else it.first.minimumProportion }
    }
    else {
      if (maximizeEditor == null) {
        val splittersToNormalize = getSplittersToNormalize(e)
        if (splittersToNormalize.isNotEmpty()) {
          maximizeEditor = false
          splittersToNormalize.forEach { it.proportion = .5f }
        }
      }
    }
    if (floatingEditor) return  //Don't normalize splitters


    if (maximizeEditor == null || maximizeEditor == true) {
      if (idsToHide.isNotEmpty()) {
        maximizeEditor = true
        val layout = toolWindowManager.layout.copy()
        toolWindowManager.clearSideStack()
        //toolWindowManager.activateEditorComponent();
        for (id in idsToHide) {
          toolWindowManager.hideToolWindow(id, false, true, ToolWindowEventSource.HideAllWindowsAction)
        }
        toolWindowManager.layoutToRestoreLater = layout
        toolWindowManager.activateEditorComponent()
      }
    }
    if (maximizeEditor == null || !maximizeEditor) {
      val restoredLayout = toolWindowManager.layoutToRestoreLater
      if (restoredLayout != null) {
        toolWindowManager.layoutToRestoreLater = null
        toolWindowManager.layout = restoredLayout
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

    fun AnActionEvent.isRelatedToSplits() : Boolean {
      return place == ActionPlaces.EDITOR_TAB && (inputEvent as MouseEvent).clickCount == 2
    }

    fun getSplittersToMaximize(e: AnActionEvent): Set<Pair<Splitter, Boolean>> {
      val project = e.project
      val editor = e.getData(CommonDataKeys.HOST_EDITOR)
      if (project == null || editor == null || !e.isRelatedToSplits()) {
        return emptySet()
      }
      return getSplittersToMaximize(project, editor)
    }

    fun getSplittersToMaximize(project: @Nullable Project, editor: @Nullable Editor): Set<Pair<Splitter, Boolean>> {
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return emptySet()
      val set = HashSet<Pair<Splitter, Boolean>>()
      var comp = editor.component as Component?
      while (comp != editorManager.mainSplitters && comp != null) {
        val parent = comp.parent
        if (parent is Splitter && UIUtil.isClientPropertyTrue(parent, EditorsSplitters.SPLITTER_KEY)) {
          if (parent.firstComponent == comp) {
            if (parent.proportion < parent.maximumProportion) {
              set.add(Pair(parent, true))
            }
          }
          else {
            if (parent.proportion > parent.minimumProportion) {
              set.add(Pair(parent, false))
            }
          }
        }
        comp = parent
      }
      return set
    }

    fun getSplittersToNormalize(e: AnActionEvent): Set<Splitter> {
      val project = e.project
      val editor = e.getData(CommonDataKeys.HOST_EDITOR)
      if (project == null || editor == null || !e.isRelatedToSplits()) {
        return emptySet()
      }
      val set = HashSet<Splitter>()
      var splitters = ComponentUtil.getParentOfType(EditorsSplitters::class.java, editor.component as Component)
      while (splitters != null) {
        val candidate = ComponentUtil.getParentOfType(EditorsSplitters::class.java, splitters.parent)
        splitters = candidate ?: break
      }
      if (splitters != null) {
        val splitterList = UIUtil.findComponentsOfType(splitters, Splitter::class.java)
        splitterList.removeIf { !UIUtil.isClientPropertyTrue(it, EditorsSplitters.SPLITTER_KEY) }
        set.addAll(splitterList)
      }
      return set
    }

    fun isThereSplitter(e: AnActionEvent): Boolean {
      val project = e.project
      val editor = e.getData(CommonDataKeys.HOST_EDITOR)
      if (project == null || editor == null) return false
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return false
      var comp = editor.component as Component
      while (comp != editorManager.mainSplitters) {
        val parent = comp.parent
        if (parent is Splitter) {
          return true
        }
        comp = parent
      }
      return false
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }
    val toolWindowManager = ToolWindowManager.getInstance(project) as? ToolWindowManagerEx
    if (toolWindowManager == null) {
      presentation.isEnabled = false
      return
    }

    presentation.isEnabled = true

    val splittersToMaximize = getSplittersToMaximize(event)
    if (splittersToMaximize.isNotEmpty()) {
      presentation.text = IdeBundle.message("action.maximize.editor")
      return
    }

    val splittersToNormalize = getSplittersToNormalize(event)
    if (splittersToNormalize.isNotEmpty()) {
      presentation.text = IdeBundle.message("action.normalize.splits")
      return
    }
    val window = event.getData(EditorWindow.DATA_KEY)
    if (window == null || !window.owner.isFloating) {
      if (getIDsToHide(toolWindowManager).isNotEmpty()) {
        presentation.setText(IdeBundle.message("action.hide.all.windows"), true)
        return
      }

      if (toolWindowManager.layoutToRestoreLater != null) {
        presentation.text = IdeBundle.message("action.restore.windows")
        return
      }
    }
    presentation.isEnabled = false
  }
}