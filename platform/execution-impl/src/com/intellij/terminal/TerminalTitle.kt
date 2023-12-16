// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.model.TerminalApplicationTitleListener
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList

interface TerminalTitleListener {
  /**
   * Can be called from any thread (not only from EDT)
   */
  fun onTitleChanged(terminalTitle: TerminalTitle)
}

class TerminalTitle {
  private val listeners = CopyOnWriteArrayList<TerminalTitleListener>()
  @Volatile
  private var state = State()

  fun change(block: State.() -> Unit) {
    val newState = state.copy()
    newState.block()
    if (newState != state) {
      state = newState
      fireTitleChanged()
    }
  }

  val userDefinedTitle: @Nls String?
    get() = state.userDefinedTitle

  val applicationTitle: @Nls String?
    get() = state.applicationTitle

  internal val trackTerminalApplicationTitleChanges: Boolean?
    get() = state.trackTerminalApplicationTitleChanges

  val tag: @Nls String?
    get() = state.tag

  val defaultTitle: @Nls String?
    get() = state.defaultTitle

  fun addTitleListener(listener: TerminalTitleListener, parentDisposable: Disposable) {
    addTitleListener(listener)
    if (!Disposer.tryRegister(parentDisposable) { removeTitleListener(listener) }) {
      removeTitleListener(listener)
    }
  }

  private fun addTitleListener(terminalTitleListener: TerminalTitleListener) {
    listeners.add(terminalTitleListener)
  }

  private fun removeTitleListener(terminalTitleListener: TerminalTitleListener) {
    listeners.remove(terminalTitleListener)
  }

  fun buildTitle(): @Nls String {
    val title = userDefinedTitle ?: shortenApplicationTitle() ?: defaultTitle ?: ExecutionBundle.message("terminal.default.title")
    return if (tag != null) "$title ($tag)" else title
  }

  fun buildFullTitle(): @Nls String {
    return userDefinedTitle ?: applicationTitle ?: defaultTitle ?: ExecutionBundle.message("terminal.default.title")
  }

  private fun shortenApplicationTitle(): String? {
    return StringUtil.trimMiddle(applicationTitle ?: return null, 30)
  }

  private fun fireTitleChanged() {
    listeners.forEach {
      it.onTitleChanged(this)
    }
  }

  override fun toString(): String = state.toString()

  data class State(var userDefinedTitle: @Nls String? = null,
                   var applicationTitle: @Nls String? = null,
                   var tag: @Nls String? = null,
                   var defaultTitle: @Nls String? = null,
                   var trackTerminalApplicationTitleChanges: Boolean? = null) {
    override fun toString(): String {
      return "userDefined=$userDefinedTitle, application=$applicationTitle, tag=$tag," +
             " default=$defaultTitle, trackTerminalApplicationTitle=$trackTerminalApplicationTitleChanges"
    }
  }
}

fun TerminalTitle.bindApplicationTitle(terminal: Terminal, parentDisposable: Disposable) {
  val listener = TerminalApplicationTitleListener { newApplicationTitle ->
    if (trackTerminalApplicationTitleChanges ?: AdvancedSettings.getBoolean("terminal.show.application.title")) {
      change {
        applicationTitle = newApplicationTitle
      }
    }
  }
  terminal.addApplicationTitleListener(listener)
  Disposer.register(parentDisposable) {
    terminal.removeApplicationTitleListener(listener)
  }
}
