// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.execution.ExecutionBundle
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.KeyStroke

@ApiStatus.Internal
object TerminalCmdKShortcutDialog {
  private const val SHORTCUT_CHOICE_PROPERTY = "terminal.cmd.k.shortcut.choice"

  private const val CHECKIN_PROJECT_ACTION_ID = "CheckinProject"
  private const val TERMINAL_CLEAR_ACTION_ID = "Terminal.ClearBuffer"

  private const val COMMIT_OPTION = 0
  private const val CLEAR_TERMINAL_OPTION = 1

  private var isDialogScheduled = false

  @JvmStatic
  @JvmOverloads
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun handleIfNeeded(
    project: Project?,
    component: Component,
    keyEvent: KeyEvent,
    clearClassicTerminalAction: Consumer<KeyEvent>? = null,
  ): Boolean {
    if (project == null ||
        !isCmdK(keyEvent) ||
        PropertiesComponent.getInstance().isValueSet(SHORTCUT_CHOICE_PROPERTY)) {
      return false
    }

    val actionManager = ActionManager.getInstance()
    val checkinAction = actionManager.getAction(CHECKIN_PROJECT_ACTION_ID) ?: return false
    val clearTerminalAction = actionManager.getAction(TERMINAL_CLEAR_ACTION_ID) ?: return false

    val shortcut = KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(keyEvent), null)
    if (!hasShortcutConflict(shortcut)) return false

    if (isDialogScheduled) return true
    val actionKeyEvent = copyKeyEvent(keyEvent, component)
    isDialogScheduled = true
    ApplicationManager.getApplication().invokeLater {
      try {
        showDialog(project, component, shortcut, actionKeyEvent, checkinAction, clearTerminalAction, clearClassicTerminalAction)
      }
      finally {
        isDialogScheduled = false
      }
    }
    return true
  }

  private fun showDialog(
    project: Project,
    component: Component,
    shortcut: KeyboardShortcut,
    keyEvent: KeyEvent,
    checkinAction: AnAction,
    clearTerminalAction: AnAction,
    clearClassicTerminalAction: Consumer<KeyEvent>?,
  ) {
    if (project.isDisposed) return

    val checkinActionText = checkinAction.templatePresentation.text ?: ActionsBundle.message("action.CheckinProject.text")
    val clearTerminalActionText = clearTerminalAction.templatePresentation.text ?: IdeBundle.message("terminal.action.ClearBuffer.text")
    val choice = Messages.showDialog(
      project,
      ExecutionBundle.message("terminal.cmd.k.shortcut.dialog.message", checkinActionText, clearTerminalActionText),
      ExecutionBundle.message("terminal.cmd.k.shortcut.dialog.title"),
      arrayOf(checkinActionText, clearTerminalActionText),
      COMMIT_OPTION,
      Messages.getInformationIcon(),
    )

    val dataContext = DataManager.getInstance().getDataContext(component)
    when (choice) {
      COMMIT_OPTION -> {
        removeClearBufferShortcut(shortcut)
        rememberChoice()
        performAction(checkinAction, dataContext, keyEvent)
      }
      CLEAR_TERMINAL_OPTION -> {
        rememberChoice()
        if (clearClassicTerminalAction != null)
          clearClassicTerminalAction.accept(keyEvent)
        else
          performAction(clearTerminalAction, dataContext, keyEvent)
      }
    }
  }

  @JvmStatic
  fun hasClearTerminalShortcut(keyEvent: KeyEvent): Boolean {
    if (!isCmdK(keyEvent)) return false

    val shortcut = KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(keyEvent), null)
    return hasActionShortcut(TERMINAL_CLEAR_ACTION_ID, shortcut)
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  private fun isCmdK(keyEvent: KeyEvent): Boolean {
    return OS.CURRENT == OS.macOS &&
           keyEvent.id == KeyEvent.KEY_PRESSED &&
           keyEvent.modifiersEx == InputEvent.META_DOWN_MASK &&
           keyEvent.keyCode == KeyEvent.VK_K
  }

  private fun hasActionShortcut(actionId: String, shortcut: KeyboardShortcut): Boolean {
    return KeymapUtil.getActiveKeymapShortcuts(actionId).shortcuts.any { it == shortcut }
  }

  private fun hasShortcutConflict(shortcut: KeyboardShortcut): Boolean =
    hasActionShortcut(CHECKIN_PROJECT_ACTION_ID, shortcut) &&
    hasActionShortcut(TERMINAL_CLEAR_ACTION_ID, shortcut)

  private fun removeClearBufferShortcut(shortcut: KeyboardShortcut) {
    val actionId = TERMINAL_CLEAR_ACTION_ID
    if (!hasActionShortcut(actionId, shortcut))
      return

    getKeymapToModify()?.removeShortcut(actionId, shortcut)
  }

  private fun rememberChoice() {
    PropertiesComponent.getInstance().setValue(SHORTCUT_CHOICE_PROPERTY, true)
  }

  private fun copyKeyEvent(keyEvent: KeyEvent, component: Component): KeyEvent {
    return KeyEvent(
      component,
      keyEvent.id,
      keyEvent.`when`,
      keyEvent.modifiersEx,
      keyEvent.keyCode,
      keyEvent.keyChar,
      keyEvent.keyLocation,
    )
  }

  private fun performAction(action: AnAction, dataContext: DataContext, keyEvent: KeyEvent) {
    val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, keyEvent)
    ActionUtil.performAction(action, event)
  }
}

/**
 * Sets the shortcut for the given action ID.
 * If the provided shortcut is null, removes all shortcuts for the action.
 * Takes care of creating a new keymap if the current one cannot be modified.
 */
@ApiStatus.Internal
fun updateActionShortcut(
  actionId: String,
  value: KeyboardShortcut?,
  replaceExistingShortcuts: Boolean = true,
) {
  val keymapToModify = getKeymapToModify() ?: return
  if (replaceExistingShortcuts)
    keymapToModify.removeAllActionShortcuts(actionId)

  value?.let { keymapToModify.addShortcut(actionId, it) }
}

private fun getKeymapToModify(): Keymap? {
  val keymapManager = KeymapManager.getInstance() as? KeymapManagerEx ?: return null

  val keymapToModify = keymapManager.activeKeymap
  return if (!keymapToModify.canModify()) {
    val allKeymaps = keymapManager.allKeymaps
    val name = SchemeNameGenerator.getUniqueName(
      KeyMapBundle.message("new.keymap.name", keymapToModify.presentableName)
    ) { newName: String ->
      allKeymaps.any { it.name == newName || it.presentableName == newName }
    }

    val newKeymap = keymapToModify.deriveKeymap(name)
    keymapManager.schemeManager.addScheme(newKeymap)
    keymapManager.activeKeymap = newKeymap
    newKeymap
  }
  else keymapToModify
}
