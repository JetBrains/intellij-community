// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.execution.ExecutionBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.RunOnceUtil
import com.intellij.idea.ActionsBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@ApiStatus.Internal
object TerminalCmdKShortcutConflictNotification {
  private const val NOTIFICATION_ID = "terminal.shortcut.conflict.notification"

  private const val CHECKIN_PROJECT_ACTION_ID = "CheckinProject"
  private const val TERMINAL_CLEAR_ACTION_ID = "Terminal.ClearBuffer"

  @JvmStatic
  @OptIn(LowLevelLocalMachineAccess::class)
  fun showIfNeeded(project: Project?, keyEvent: KeyEvent) {
    if (project == null ||
        OS.CURRENT != OS.macOS ||
        keyEvent.id != KeyEvent.KEY_PRESSED ||
        keyEvent.keyCode != KeyEvent.VK_K ||
        keyEvent.modifiersEx != InputEvent.META_DOWN_MASK) {
      return
    }

    val actionManager = ActionManager.getInstance()
    val ideAction = actionManager.getAction(CHECKIN_PROJECT_ACTION_ID) ?: return
    val shortcut = KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(keyEvent), null)
    if (KeymapUtil.getActiveKeymapShortcuts(CHECKIN_PROJECT_ACTION_ID).shortcuts.none { it == shortcut }) {
      return
    }

    val ideActionText = ideAction.templatePresentation.text ?: ActionsBundle.message("action.CheckinProject.text")
    val terminalActionText = actionManager.getAction(TERMINAL_CLEAR_ACTION_ID)?.templatePresentation?.text
                             ?: IdeBundle.message("terminal.action.ClearBuffer.text")
    showNotification(project, shortcut, ideActionText, terminalActionText)
  }

  private fun showNotification(
    project: Project,
    shortcut: KeyboardShortcut,
    ideActionText: String,
    terminalActionText: String,
  ) {
    RunOnceUtil.runOnceForApp(NOTIFICATION_ID) {
      val shortcutText = KeymapUtil.getShortcutText(shortcut)
      val useTerminalShortcut = NotificationAction.createSimpleExpiring(
        ExecutionBundle.message("terminal.shortcut.conflict.notification.use.terminal.action", shortcutText, terminalActionText)
      ) {
        updateActionShortcut(TERMINAL_CLEAR_ACTION_ID, shortcut, replaceExistingShortcuts = false)
      }

      NotificationGroupManager.getInstance()
        .getNotificationGroup("terminal")
        .createNotification(
          ExecutionBundle.message("terminal.shortcut.conflict.notification.title"),
          ExecutionBundle.message("terminal.shortcut.conflict.notification.content", shortcutText, ideActionText, terminalActionText),
          NotificationType.WARNING
        )
        .addAction(useTerminalShortcut)
        .notify(project)
    }
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