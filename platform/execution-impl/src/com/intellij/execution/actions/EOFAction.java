// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.google.common.base.Ascii;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public final class EOFAction extends DumbAwareAction {

  public static final @NonNls String ACTION_ID = "SendEOF";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RunContentDescriptor descriptor = StopAction.getRecentlyStartedContentDescriptor(e.getDataContext());
    boolean isConsoleSelected = descriptor != null && isConsoleSelected(descriptor);
    ProcessHandler handler = descriptor != null ? descriptor.getProcessHandler() : null;
    e.getPresentation().setEnabledAndVisible(isConsoleSelected
                                             && handler != null
                                             && !handler.isProcessTerminated());
  }

  private static boolean isConsoleSelected(@NotNull RunContentDescriptor descriptor) {
    RunnerLayoutUi runnerLayoutUi = descriptor.getRunnerLayoutUi();
    if (runnerLayoutUi == null) return false;
    ContentManager contentManager = runnerLayoutUi.getContentManager();
    Content selectedContent = contentManager.getSelectedContent();
    return selectedContent != null
           && ExecutionConsole.CONSOLE_CONTENT_ID.equals(selectedContent.getUserData(ViewImpl.ID));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RunContentDescriptor descriptor = StopAction.getRecentlyStartedContentDescriptor(e.getDataContext());
    ProcessHandler activeProcessHandler = descriptor != null ? descriptor.getProcessHandler() : null;
    if (activeProcessHandler == null || activeProcessHandler.isProcessTerminated()) return;

    ConsoleView console = e.getData(LangDataKeys.CONSOLE_VIEW);
    if (console instanceof TerminalExecutionConsole) {
      sendEOFToPtyProcess(activeProcessHandler.getProcessInput());
      return;
    }

    try (OutputStream input = activeProcessHandler.getProcessInput()) {
      if (input != null) {
        if (console != null) {
          console.print("^D\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        }
      }
    }
    catch (IOException ignored) {
    }
  }

  /**
   * Writes the EOF (end of file) character to process's stdin (PTY).
   * This character causes the pending tty buffer to be sent to the waiting user program without waiting for end-of-line.
   * If it is the first character of the line, the read(2) in the user program returns 0, which signifies end-of-file.
   *
   * <p>Works on Unix and Windows.
   *
   * @see <a href="https://man7.org/linux/man-pages/man3/tcflow.3.html">termios(3)</a>
   * @see <a href="https://www.gnu.org/software/libc/manual/html_node/Editing-Characters.html">Characters for Input Editing</a>
   */
  private static void sendEOFToPtyProcess(OutputStream outputStream) {
    if (outputStream != null) {
      try {
        outputStream.write(Ascii.EOT);
        outputStream.flush();
      }
      catch (IOException ignored) {
      }
    }
  }
}
