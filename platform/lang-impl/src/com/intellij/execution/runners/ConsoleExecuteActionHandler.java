/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author traff
 */
public class ConsoleExecuteActionHandler extends BaseConsoleExecuteActionHandler {
  private ProcessHandler myProcessHandler;

  public ConsoleExecuteActionHandler(ProcessHandler processHandler, boolean preserveMarkup) {
    super(preserveMarkup);

    myProcessHandler = processHandler;
  }

  @Nullable
  private synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public synchronized void setProcessHandler(@NotNull final ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  @Override
  protected void execute(@NotNull String text) {
    processLine(text);
  }

  public void processLine(String line) {
    sendText(line + "\n");
  }

  public void sendText(String line) {
    final Charset charset = myProcessHandler instanceof BaseOSProcessHandler ?
                            ((BaseOSProcessHandler)myProcessHandler).getCharset() : null;
    final ProcessHandler handler = getProcessHandler();
    assert handler != null : "process handler is null";
    final OutputStream outputStream = handler.getProcessInput();
    assert outputStream != null : "output stream is null";
    try {
      byte[] bytes = charset != null ? (line + "\n").getBytes(charset) : line.getBytes();
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      // ignore
    }
  }

  public final boolean isProcessTerminated() {
    final ProcessHandler handler = getProcessHandler();
    return handler == null || handler.isProcessTerminated();
  }
}