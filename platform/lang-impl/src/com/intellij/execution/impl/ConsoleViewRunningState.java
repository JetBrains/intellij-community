/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

public class ConsoleViewRunningState extends ConsoleState {
  private final ConsoleViewImpl myConsole;
  private final ProcessHandler myProcessHandler;
  private final ConsoleState myFinishedStated;
  private final Writer myUserInputWriter;

  private final ProcessAdapter myProcessListener = new ProcessAdapter() {
    @Override
    public void onTextAvailable(final ProcessEvent event, final Key outputType) {
      myConsole.print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
    }
  };

  public ConsoleViewRunningState(final ConsoleViewImpl console, final ProcessHandler processHandler,
                                 final ConsoleState finishedStated,
                                 final boolean attachToStdOut,
                                 final boolean attachToStdIn) {

    myConsole = console;
    myProcessHandler = processHandler;
    myFinishedStated = finishedStated;

    // attach to process stdout
    if (attachToStdOut) {
      processHandler.addProcessListener(myProcessListener);
    }

    // attach to process stdin
    if (attachToStdIn) {
      final OutputStream processInput = myProcessHandler.getProcessInput();
      myUserInputWriter = processInput != null ? createOutputStreamWriter(processInput, processHandler) : null;
    }
    else {
      myUserInputWriter = null;
    }
  }

  private static OutputStreamWriter createOutputStreamWriter(OutputStream processInput, ProcessHandler processHandler) {
    Charset charset = null;
    if (processHandler instanceof OSProcessHandler) {
      charset = ((OSProcessHandler)processHandler).getCharset();
    }
    if (charset == null) {
      charset = ObjectUtils.notNull(EncodingManager.getInstance().getDefaultCharset(), CharsetToolkit.UTF8_CHARSET);
    }
    return new OutputStreamWriter(processInput, charset);
  }

  @Override
  @NotNull
  public ConsoleState dispose() {
    if (myProcessHandler != null) {
      myProcessHandler.removeProcessListener(myProcessListener);
    }
    return myFinishedStated;
  }

  @Override
  public boolean isFinished() {
    return myProcessHandler == null || myProcessHandler.isProcessTerminated();
  }

  @Override
  public boolean isRunning() {
    return myProcessHandler != null && !myProcessHandler.isProcessTerminated();
  }

  @Override
  public void sendUserInput(final String input) throws IOException {
    if (myUserInputWriter == null) {
      throw new IOException(ExecutionBundle.message("no.user.process.input.error.message"));
    }
    myUserInputWriter.write(input);
    myUserInputWriter.flush();
  }

  @Override
  public ConsoleState attachTo(final ConsoleViewImpl console, final ProcessHandler processHandler) {
    return dispose().attachTo(console, processHandler);
  }

  @Override
  public String toString() {
    return "Running state";
  }
}