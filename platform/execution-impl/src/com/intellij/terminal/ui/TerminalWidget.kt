// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @get:ApiStatus.Internal
  val session: TerminalSession?

  /**
   * Makes this terminal widget handle output events from this [session] and send input events to it.
   *
   * Note that session lifecycle is not bound to the lifecycle of the widget.
   * If the widget is disposed, the session will continue running.
   * To close the session, send [com.intellij.terminal.session.TerminalCloseEvent] using [TerminalSession.sendInputEvent].
   */
  @ApiStatus.Internal
  fun connectToSession(session: TerminalSession)

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
   * Note that implementations might not guarantee that the result is 100% correct.
   */
  @ApiStatus.Experimental
  @RequiresEdt(generateAssertion = false)
  fun isCommandRunning(): Boolean {
    return false
  }

  @RequiresEdt(generateAssertion = false)
  fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable)
}

fun TerminalWidget.setNewParentDisposable(newParentDisposable: Disposable) {
  Disposer.register(newParentDisposable, this)
  val jediTermWidget = JBTerminalWidget.asJediTermWidget(this)
  if (jediTermWidget != null) {
    Disposer.register(newParentDisposable, jediTermWidget)
  }
}
