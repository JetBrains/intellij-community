// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

var Action.name: @Nls String?
  get() = getValue(Action.NAME) as? String
  set(value) {
    putValue(Action.NAME, value)
  }

fun Action.toAnAction(): AnAction {
  val action = this
  return object : DumbAwareAction(action.name.orEmpty()) {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = action.isEnabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      val actionEvent = ActionEvent(event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
                                    ActionEvent.ACTION_PERFORMED,
                                    "execute",
                                    event.modifiers)
      action.actionPerformed(actionEvent)
    }
  }
}

fun swingAction(name: @Nls String, action: (ActionEvent) -> Unit) = object : AbstractAction(name) {
  override fun actionPerformed(e: ActionEvent) {
    action(e)
  }
}