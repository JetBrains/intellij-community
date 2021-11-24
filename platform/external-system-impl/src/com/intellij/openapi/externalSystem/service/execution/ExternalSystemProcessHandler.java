// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;

/**
 * @author Vladislav.Soroka
 */
public class ExternalSystemProcessHandler extends BuildProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor, Disposable {
  private static final Logger LOG = Logger.getInstance(ExternalSystemProcessHandler.class);
  private final String myExecutionName;
  @Nullable
  private ExternalSystemTask myTask;
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();
  @Nullable
  private OutputStream myProcessInput;

  public ExternalSystemProcessHandler(@NotNull ExternalSystemTask task, String executionName) {
    myTask = task;
    myExecutionName = executionName;
    if (task instanceof UserDataHolder) {
      UserDataHolder dataHolder = (UserDataHolder)task;
      InputStream stream = dataHolder.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
      if (stream != null) {
        LOG.warn("Unexpected stream found, closing it...");
        StreamUtil.closeStream(stream);
      }
      try {
        Pipe pipe = Pipe.open();
        InputStream inputStream = new BufferedInputStream(Channels.newInputStream(pipe.source()));
        myProcessInput = new BufferedOutputStream(Channels.newOutputStream(pipe.sink()));
        dataHolder.putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, inputStream);
      }
      catch (IOException e) {
        LOG.warn("Unable to setup process input", e);
      }
    }
  }

  @Override
  public String getExecutionName() {
    return myExecutionName;
  }

  @Nullable
  public ExternalSystemTask getTask() {
    return myTask;
  }

  @Override
  public void notifyTextAvailable(@NotNull final String text, @NotNull final Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  @Override
  protected void destroyProcessImpl() {
    ExternalSystemTask task = myTask;
    if (task != null) {
      task.cancel();
    }
    closeInput();
  }

  @Override
  protected void detachProcessImpl() {
    try {
      notifyProcessDetached();
    }
    finally {
      closeInput();
    }
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
    try {
      super.notifyProcessTerminated(exitCode);
    }
    finally {
      closeInput();
    }
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }

  protected void closeInput() {
    StreamUtil.closeStream(myProcessInput);
    myProcessInput = null;
    if (myTask instanceof UserDataHolder) {
      UserDataHolder taskDataHolder = (UserDataHolder)myTask;
      InputStream inputStream = taskDataHolder.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
      taskDataHolder.putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, null);
      StreamUtil.closeStream(inputStream);
    }
  }

  @Override
  public void dispose() {
    try {
      detachProcessImpl();
    }
    finally {
      myTask = null;
    }
  }
}
