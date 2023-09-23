// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.Link
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JPanel

private var Action.text
  get() = getValue(Action.NAME)
  set(value) = putValue(Action.NAME, value)

class SuggestAutomaticVfsRecoveryDialog(private val isRestartCapable: Boolean, private val setEnableSuggestion: (Boolean) -> Unit) : DialogWrapper(null) {
  private val restartOrExit: @Nls String = if (isRestartCapable)
    IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.restart")
  else
    IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.exit")

  init {
    title = IdeBundle.message("dialog.title.recover.vfs.automatic.suggestion")
    isResizable = false

    okAction.text = IdeBundle.message("button.recover.vfs.automatic.suggestion.recover.and.restart", restartOrExit)
    cancelAction.text = IdeBundle.message("button.cancel.without.mnemonic")

    setDoNotAskOption(object : com.intellij.openapi.ui.DoNotAskOption.Adapter() {
      override fun getDoNotShowMessage(): String = IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.dont.suggest")

      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        setEnableSuggestion(!isSelected)
      }

      override fun shouldSaveOptionsOnCancel(): Boolean = true
    })
    init()
  }

  override fun createCenterPanel() = panel {
    row {
      text(IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.error"))
    }
    row {
      text(IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.offer", restartOrExit))
    }
    row {
      text(IdeBundle.message("dialog.message.recover.vfs.automatic.suggestion.invoke.later",
                             ActionsBundle.message("action.RecoverCachesFromLog.text")))
    }
  }

  override fun createSouthAdditionalPanel(): JPanel {
    val link = Link(IdeBundle.message("link.recover.vfs.choose.recovery.point")) {
      close(CHOOSE_RECOVERY_POINT_CODE)
    }
    val panel = NonOpaquePanel(BorderLayout())
    panel.border = JBUI.Borders.empty(0, 5)
    panel.add(link)
    return panel
  }

  companion object {
    const val CHOOSE_RECOVERY_POINT_CODE = NEXT_USER_EXIT_CODE + 3
  }
}