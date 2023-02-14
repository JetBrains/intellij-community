// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.TerminalTitle
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

interface TerminalWidget : ComponentContainer {
  val terminalTitle: TerminalTitle

  /**
   * terminal size; null, if the terminal size is 0x0, e.g. the component is not laid out yet
   */
  val termSize: TermSize?

  fun connectToTty(ttyConnector: TtyConnector)

  val ttyConnectorAccessor: TtyConnectorAccessor

  val ttyConnector: TtyConnector?
    get() = ttyConnectorAccessor.ttyConnector

  fun writePlainMessage(message: @Nls String)

  fun setCursorVisible(visible: Boolean)

  fun hasFocus(): Boolean

  fun requestFocus()

  /**
   * Adds a custom notification component to the top of the terminal.
   */
  fun addNotification(notificationComponent: JComponent, disposable: Disposable)

  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable)
}

fun TerminalWidget.setNewParentDisposable(newParentDisposable: Disposable) {
  Disposer.register(newParentDisposable, this)
  val jediTermWidget = JBTerminalWidget.asJediTermWidget(this)
  if (jediTermWidget != null) {
    Disposer.register(newParentDisposable, jediTermWidget)
  }
}
