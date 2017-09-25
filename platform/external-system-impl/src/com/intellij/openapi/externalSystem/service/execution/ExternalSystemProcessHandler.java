/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * @author Vladislav.Soroka
 */
public class ExternalSystemProcessHandler extends ProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private static final Logger LOG = Logger.getInstance(ExternalSystemProcessHandler.class);
  private final ExternalSystemTask myTask;
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();
  @Nullable
  private OutputStream myProcessInput;

  public ExternalSystemProcessHandler(ExternalSystemTask task) {
    myTask = task;
    if (task instanceof UserDataHolder) {
      try {
        PipedInputStream inputStream = new PipedInputStream();
        myProcessInput = new PipedOutputStream(inputStream);
        ((UserDataHolder)task).putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, inputStream);
      }
      catch (IOException e) {
        LOG.warn("Unable to setup process input", e);
      }
    }
  }

  @Override
  public void notifyTextAvailable(@NotNull final String text, @NotNull final Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  @Override
  protected void destroyProcessImpl() {
    myTask.cancel();
    closeInput();
  }

  @Override
  protected void detachProcessImpl() {
    notifyProcessDetached();
    closeInput();
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Nullable
  @Override
  public OutputStream getProcessInput() {
    return myProcessInput;
  }

  @Override
  public void notifyProcessTerminated(int exitCode) {
    super.notifyProcessTerminated(exitCode);
    closeInput();
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }

  protected void closeInput() {
    StreamUtil.closeStream(myProcessInput);
    myProcessInput = null;
  }
}
