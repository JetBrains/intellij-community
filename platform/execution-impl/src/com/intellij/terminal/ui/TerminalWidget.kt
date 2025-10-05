// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.terminal.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

interface TerminalWidget : ComponentContainer {
  val terminalTitle: TerminalTitle

  /**
   * Terminal size in characters according to an underlying UI component;
   * null, if unavailable, e.g. the component is not shown or not laid out yet
   */
  val termSize: TermSize?

  /**
   * Returns the future that will be completed once the widget's component is added to the UI hierarchy and resized.
   */
  @ApiStatus.Experimental
  fun getTerminalSizeInitializedFuture(): CompletableFuture<TermSize>

  /**
   * Command used to run the session related to this widget
   * todo: would be great to find better place for it
   */
  var shellCommand: List<String>?

  fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize)

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

  @RequiresEdt(generateAssertion = false)
  fun sendCommandToExecute(shellCommand: String)

  /**
   * Returns the **immutable** state of the terminal output text.
   */
  @ApiStatus.Experimental
  @RequiresEdt(generateAssertion = false)
  fun getText(): CharSequence {
    return ""
  }

  /**
   * Note that implementations might not guarantee that the result is 100% correct.
   */
  @ApiStatus.Experimental
  @RequiresEdt(generateAssertion = false)
  fun isCommandRunning(): Boolean {
    return false
  }

  /**
   * Returns the OS-dependent absolute path to the current working directory of the shell.
   * Note that due to OS and shell-dependent way of computing the value, it might not be available.
   */
  @ApiStatus.Experimental
  fun getCurrentDirectory(): String? {
    return null
  }

  @RequiresEdt(generateAssertion = false)
  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable)

  @Deprecated("TerminalSession was moved to the terminal plugin: org.jetbrains.plugins.terminal.session.TerminalSession")
  val session: TerminalSession?
    get() = throw UnsupportedOperationException("Deprecated")

  @Deprecated("TerminalSession was moved to the terminal plugin: org.jetbrains.plugins.terminal.session.TerminalSession")
  fun connectToSession(session: TerminalSession) {
    throw UnsupportedOperationException("Deprecated")
  }
}

fun TerminalWidget.setNewParentDisposable(newParentDisposable: Disposable) {
  Disposer.register(newParentDisposable, this)
  val jediTermWidget = JBTerminalWidget.asJediTermWidget(this)
  if (jediTermWidget != null) {
    Disposer.register(newParentDisposable, jediTermWidget)
  }
}
