// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

class HelpAction(val component : JComponent) : AbstractAction("Help") {
  private val helpAction : Action? = getHelpAction()

  val isAvailable = helpAction != null

  private fun getHelpAction(): Action? {
    val dialog = DialogWrapper.findInstance(component)
    if (dialog != null) {
      try {
        val getHelpAction = DialogWrapper::class.java.getDeclaredMethod("getHelpAction")
        getHelpAction.isAccessible = true
        val helpAction = getHelpAction.invoke(dialog)
        if (helpAction is Action && helpAction.isEnabled) {
          return helpAction
        }
      }
      catch (e: Exception) {
        e.printStackTrace()
      }
    }
    return null
  }

  override fun actionPerformed(e: ActionEvent) {
    helpAction?.actionPerformed(e)
  }
}