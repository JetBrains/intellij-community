// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.intellij.terminal.ui.TerminalWidget
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets

@ApiStatus.Internal
class TerminalDashboard(
  private val project: Project,
  private val parentDisposable: Disposable,
) {

  suspend fun createWidget(ptyProcess: PtyProcess, initialSize: TermSize): TerminalWidget {
    val connector = PtyProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8)
    return withContext(Dispatchers.EDT) {
      val widget = JBTerminalWidget(project, JBTerminalSystemSettingsProviderBase(), parentDisposable)
      widget.asNewWidget().also { it.connectToTty(connector, initialSize) }
    }
  }
}
