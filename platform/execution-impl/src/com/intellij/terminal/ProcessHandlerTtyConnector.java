// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.PtyBasedProcess;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remote.RemoteProcess;
import com.intellij.util.ObjectUtils;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ProcessHandlerTtyConnector implements TtyConnector {

  private static final Logger LOG = Logger.getInstance(ProcessHandlerTtyConnector.class);

  private final ProcessHandler myProcessHandler;
  private final Process myPtyProcess;
  private final Charset myCharset;

  public ProcessHandlerTtyConnector(@NotNull ProcessHandler processHandler, @NotNull Charset charset) {
    myProcessHandler = processHandler;
    myPtyProcess = getPtyProcess(processHandler);
    myCharset = charset;
  }

  private static @Nullable Process getPtyProcess(@NotNull ProcessHandler processHandler) {
    if (!(processHandler instanceof BaseProcessHandler)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ProcessHandler doesn't support terminal window resizing: " + processHandler.getClass());
      }
      return null;
    }
    Process process = ((BaseProcessHandler<?>)processHandler).getProcess();
    if (!(process instanceof PtyProcess) && !(process instanceof PtyBasedProcess)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Process doesn't support terminal window resizing: " + process.getClass());
      }
    }
    return process;
  }

  @Override
  public void close() {
    // ProcessHandler shouldn't be disposed silently on TerminalExecutionConsole disposing.
    // Normally, an attempt to close a console attached to a running process should be handled by
    // BaseContentCloseListener which may ask user what do to. Alternatively, a client may handle it on its own.
    // Generally, ConsoleView doesn't own an attached ProcessHandler instance.
  }

  @Override
  public void resize(@NotNull TermSize termSize) {
    if (myPtyProcess instanceof PtyProcess ptyProcess) {
      if (ptyProcess.isAlive()) {
        ptyProcess.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
      }
    }
    else if (myPtyProcess instanceof PtyBasedProcess ptyBasedProcess) {
      ptyBasedProcess.setWindowSize(termSize.getColumns(), termSize.getRows());
    }
    else if (myPtyProcess instanceof RemoteProcess remoteProcess) {
      remoteProcess.setWindowSize(termSize.getColumns(), termSize.getRows());
    }
  }

  @Override
  public String getName() {
    //noinspection HardCodedStringLiteral
    return "TtyConnector:" + myProcessHandler.toString();
  }

  @Override
  public int read(char[] buf, int offset, int length) throws IOException {
    throw new IllegalStateException("all reads should be performed by ProcessHandler");
  }

  @Override
  public void write(byte[] bytes) throws IOException {
    writeBytes(bytes);
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public void write(String string) throws IOException {
    writeBytes(string.getBytes(myCharset));
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myPtyProcess.waitFor();
  }

  @Override
  public boolean ready() throws IOException {
    return false;
  }

  private void writeBytes(byte[] bytes) throws IOException {
    OutputStream input = myProcessHandler.getProcessInput();
    if (input != null) {
      input.write(bytes);
      input.flush();
    }
  }

  public @NotNull ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public @Nullable PtyProcess getPtyProcess() {
    return ObjectUtils.tryCast(myPtyProcess, PtyProcess.class);
  }
}
