// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.PtyBasedProcess;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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
  public boolean init(Questioner q) {
    return true;
  }

  @Override
  public void close() {
    myProcessHandler.destroyProcess();
  }

  @Override
  public void resize(@NotNull Dimension termSize) {
    if (myPtyProcess instanceof PtyProcess ptyProcess) {
      if (ptyProcess.isAlive()) {
        ptyProcess.setWinSize(new WinSize(termSize.width, termSize.height));
      }
    }
    else if (myPtyProcess instanceof PtyBasedProcess ptyBasedProcess) {
      ptyBasedProcess.setWindowSize(termSize.width, termSize.height);
    }
  }

  @Override
  public String getName() {
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
