// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.SizedIcon
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference

/**
 * Toggles tool window visibility.
 * Usually shown in View|Tool-windows submenu.
 * Dynamically registered in Settings|Keymap for each newly registered tool window.
 */
open class ActivateToolWindowAction protected constructor(val toolWindowId: String)
  : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  companion object {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @JvmStatic
    @Deprecated("Use ActivateToolWindowAction.Manager explicitly")
    @ApiStatus.ScheduledForRemoval
    fun getActionIdForToolWindow(id: String): @NonNls String = Manager.getActionIdForToolWindow(id)
  }

  object Manager {
    internal fun ensureToolWindowActionRegistered(toolWindow: ToolWindow, actionManager: ActionManager) {
      val actionId = getActionIdForToolWindow(toolWindow.id)
      var action = actionManager.getAction(actionId)
      if (action == null) {
        action = ActivateToolWindowAction(toolWindow.id)
        updatePresentation(action.getTemplatePresentation(), toolWindow)
        actionManager.registerAction(actionId, action)
      }
    }

    internal fun unregister(id: String) {
      ActionManager.getInstance().unregisterAction(getActionIdForToolWindow(id))
    }

    internal fun updateToolWindowActionPresentation(toolWindow: ToolWindow) {
      val action = ActionManager.getInstance().getAction(getActionIdForToolWindow(toolWindow.id))
      if (action is ActivateToolWindowAction) {
        updatePresentation(action.getTemplatePresentation(), toolWindow)
      }
    }

    /**
     * This is the "rule" method constructs `ID` of the action for activating tool window
     * with specified `ID`.
     *
     * @param id `id` of tool window to be activated.
     */
    @JvmStatic
    fun getActionIdForToolWindow(id: String): @NonNls String = "Activate${id.replace(" ", "")}ToolWindow"

    /**
     * @return mnemonic for action if it has Alt+digit/Meta+digit shortcut.
     * Otherwise, the method returns `-1`.
     * Meta-mask is OK for Mac OS X user, because Alt+digit types strange characters into the editor.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun getMnemonicForToolWindow(toolWindowId: String): Int {
      val activeKeymap = KeymapManager.getInstance().activeKeymap
      for (shortcut in activeKeymap.getShortcuts(getActionIdForToolWindow(toolWindowId))) {
        if (shortcut !is KeyboardShortcut) {
          continue
        }

        val keyStroke = shortcut.firstKeyStroke
        val modifiers = keyStroke.modifiers
        @Suppress("DEPRECATION")
        if (modifiers == (InputEvent.ALT_DOWN_MASK or InputEvent.ALT_MASK) ||
            modifiers == InputEvent.ALT_MASK ||
            modifiers == InputEvent.ALT_DOWN_MASK ||
            modifiers == InputEvent.META_DOWN_MASK or InputEvent.META_MASK ||
            modifiers == InputEvent.META_MASK ||
            modifiers == InputEvent.META_DOWN_MASK) {
          val keyCode = keyStroke.keyCode
          if (KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
            return ('0'.code + keyCode - KeyEvent.VK_0).toChar().code
          }
        }
      }
      return -1
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.putClientProperty(ActionUtil.SHOW_ICON_IN_MAIN_MENU, true)
    val project = getEventProject(e)?.takeIf { !it.isDisposed }
    val presentation = e.presentation
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    if (toolWindow == null) {
      presentation.isEnabledAndVisible = hasEmptyState(project)
    }
    else {
      presentation.isVisible = true
      val available = toolWindow.isAvailable || hasEmptyState(project)
      if (e.place == ActionPlaces.POPUP) {
        presentation.isVisible = available
      }
      else {
        presentation.isEnabled = available
      }

      updatePresentation(presentation, toolWindow)
    }
  }

  protected open fun hasEmptyState(project: Project): Boolean = false

  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return

    val toolWindowManager = ToolWindowManager.getInstance(project)
    val source = when {
      e.inputEvent is KeyEvent -> ToolWindowEventSource.ActivateActionKeyboardShortcut
      ActionPlaces.MAIN_MENU == e.place -> ToolWindowEventSource.ActivateActionMenu
      ActionPlaces.ACTION_SEARCH == e.place -> ToolWindowEventSource.ActivateActionGotoAction
      else -> ToolWindowEventSource.ActivateActionOther
    }

    if (toolWindowManager.isEditorComponentActive || toolWindowId != toolWindowManager.activeToolWindowId) {
      val toolWindow = toolWindowManager.getToolWindow(toolWindowId)
      if (toolWindow != null) {
        if (hasEmptyState(project) && !toolWindow.isAvailable) {
          toolWindow.isAvailable = true
        }
        if (toolWindowManager is ToolWindowManagerImpl) {
          toolWindowManager.activateToolWindow(id = toolWindowId, runnable = null, autoFocusContents = true, source = source)
        }
        else {
          toolWindow.activate(null)
        }
      }
      else if (hasEmptyState(project)) {
        createEmptyState(project)
      }
    }
    else if (toolWindowManager is ToolWindowManagerImpl) {
      toolWindowManager.hideToolWindow(id = toolWindowId, hideSide = false, moveFocus = true, removeFromStripe = false, source = source)
    }
    else {
      toolWindowManager.getToolWindow(toolWindowId)?.hide(null)
    }
  }

  protected open fun createEmptyState(project: Project) {
  }
}

private fun updatePresentation(presentation: Presentation, toolWindow: ToolWindow) {
  updatePresentationImpl(
    presentation = presentation,
    toolWindowId = toolWindow.id,
    projectRef = WeakReference(toolWindow.project),
    toolWindowRef = WeakReference(toolWindow),
    fallbackStripeTitleText = toolWindow.stripeTitleProvider.get(),
  )
}

/**
 * Avoid accidentally capturing more than we need by extracting a method
 */
private fun updatePresentationImpl(
  presentation: Presentation,
  toolWindowId: String,
  projectRef: Reference<Project>,
  toolWindowRef: Reference<ToolWindow>,
  fallbackStripeTitleText: @NlsContexts.TabTitle String,
) {
  presentation.setText { toolWindowRef.get()?.stripeTitleProvider?.get() ?: fallbackStripeTitleText }
  presentation.setDescription {
    IdeBundle.message("action.activate.tool.window", toolWindowRef.get()?.stripeTitleProvider?.get() ?: fallbackStripeTitleText)
  }

  presentation.iconSupplier = SynchronizedClearableLazy label@{
    val project = projectRef.get()?.takeIf { !it.isDisposed } ?: return@label null
    val icon = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.icon
    if (icon is ScalableIcon && isNewUI()) {
      return@label icon.scale(scale(16f) / icon.getIconWidth())
    }
    if (icon == null) null else SizedIcon(icon, icon.iconHeight, icon.iconHeight)
  }
}