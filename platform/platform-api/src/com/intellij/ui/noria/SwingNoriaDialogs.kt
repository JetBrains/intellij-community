// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.noria

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

private class NoriaDialogWrapper(val dialogProps: DialogProps) : DialogWrapper(dialogProps.project, dialogProps.canBeParent) {
  fun closeImpl(exitCode: Int) = close(exitCode)

  val handle = object : NoriaDialogHandle {
    override fun close(exitCode: Int) {
      closeImpl(exitCode)
    }

    override fun shake() {

    }
  }

  val roots = arrayListOf<NoriaHandle<JComponent>>()

  fun jPanel(center: Element?): JPanel? {
    if (center == null) return null
    val root = JPanel()
    val handle = mount(disposable, center, root, SwingNoriaDialogs.toolkit)
    roots.add(handle)
    return root
  }

  val northPanel = jPanel(dialogProps.north)
  val centerPanel = jPanel(dialogProps.center)

  override fun getPreferredFocusedComponent(): JComponent? =
    roots.map { it.getPreferredFocusedNode() }.filterNotNull().firstOrNull()

  override fun createCenterPanel(): JComponent? {
    return centerPanel
  }

  override fun createNorthPanel(): JComponent? {
    return northPanel
  }

  override fun getHelpId(): String? {
    return dialogProps.helpId
  }

  fun makeAction(a: NoriaAction): Action =
    if (a.isExclusive) {
      object : DialogWrapperAction(a.name) {
        override fun doAction(e: ActionEvent?) {
          a.lambda.accept(handle)
        }
      }
    }
    else {
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          a.lambda.accept(handle)
        }
      }.apply { putValue(Action.NAME, a.name) }
    }.apply {
      putValue(DEFAULT_ACTION, if (a.role == ActionRole.Default) true else null)
      putValue(FOCUSED_ACTION, a.focused)
      putValue(Action.MNEMONIC_KEY, a.mnemonic?.toInt())
      track(myDisposable) {
        isEnabled = a.enabled.value
      }
    }

  override fun createActions(): Array<out Action> {
    var actions = dialogProps.actions.map { makeAction(it) }
    if (dialogProps.helpId != null) {
      actions += helpAction
    }
    if (SystemInfo.isMac) {
      actions = actions.reversed()
    }
    return actions.toTypedArray()
  }

  override fun createLeftSideActions(): Array<out Action> {
    return dialogProps.leftSideActions.map { makeAction(it) }.toTypedArray()
  }

  public override fun init() {
    super.init()
  }

  public override fun setErrorText(text: String?) {
    super.setErrorText(text)
  }
}

class SwingNoriaDialogs : NoriaDialogs {
  companion object {
    val toolkit = SwingToolkit()
  }

  override fun show(dialogProps: DialogProps): NoriaDialogHandle {
    val dw = NoriaDialogWrapper(dialogProps)
    dw.init()
    track(dw.disposable) {
      dw.setErrorText(dialogProps.errorText.value)
    }
    dw.show()
    return dw.handle
  }
}
