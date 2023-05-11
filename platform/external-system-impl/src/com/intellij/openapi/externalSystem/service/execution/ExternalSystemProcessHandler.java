// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.util.DiscardingInputStream;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;

/**
 * @author Vladislav.Soroka
 */
public class ExternalSystemProcessHandler extends BuildProcessHandler implements Disposable {

  private static final Logger LOG = Logger.getInstance(ExternalSystemProcessHandler.class);

  private final @NotNull String myExecutionName;
  private @Nullable ExternalSystemTask myTask;
  private @Nullable UserDataHolder myDataHolder;
  private @Nullable OutputStream myProcessInputWriter;
  private @Nullable InputStream myProcessInputReader;

  private final @NotNull AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public ExternalSystemProcessHandler(@NotNull ExternalSystemTask task, @NotNull String executionName) {
    this(task, executionName, ObjectUtils.tryCast(task, UserDataHolder.class));
  }

  private ExternalSystemProcessHandler(
    @NotNull ExternalSystemTask task,
    @NotNull String executionName,
    @Nullable UserDataHolder dataHolder
  ) {
    myTask = task;
    myDataHolder = dataHolder;
    myExecutionName = executionName;
    try {
      Pipe pipe = Pipe.open();
      myProcessInputReader = new DiscardingInputStream(new BufferedInputStream(Channels.newInputStream(pipe.source())));
      myProcessInputWriter = new BufferedOutputStream(Channels.newOutputStream(pipe.sink()));
    }
    catch (IOException e) {
      LOG.warn("Unable to setup process input", e);
    }
    if (myDataHolder != null) {
      closeLeakedStream(myDataHolder);
      myDataHolder.putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, myProcessInputReader);
    }
  }

  @Override
  public @NotNull String getExecutionName() {
    return myExecutionName;
  }

  @Nullable
  public ExternalSystemTask getTask() {
    return myTask;
  }

  @Override
  public void notifyTextAvailable(@NotNull final String text, @NotNull final Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, (decodedText, attributes) ->
      super.notifyTextAvailable(decodedText, attributes)
    );
  }

  @Override
  protected void destroyProcessImpl() {
    try {
      ExternalSystemTask task = myTask;
      if (task != null) {
        task.cancel();
      }
    }
    finally {
      closeInput();
    }
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
    return myProcessInputWriter;
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

  protected void closeInput() {
    var processInputWriter = myProcessInputWriter;
    var processInputReader = myProcessInputReader;
    var dataHolder = myDataHolder;
    myProcessInputWriter = null;
    myProcessInputReader = null;
    if (dataHolder != null) {
      dataHolder.putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, null);
    }
    //noinspection deprecation
    StreamUtil.closeStream(processInputWriter);
    //noinspection deprecation
    StreamUtil.closeStream(processInputReader);
  }

  private static void closeLeakedStream(@NotNull UserDataHolder dataHolder) {
    var leakedStream = dataHolder.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY);
    dataHolder.putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, null);
    if (leakedStream != null) {
      LOG.warn("Unexpected stream found, closing it...");
    }
    //noinspection deprecation
    StreamUtil.closeStream(leakedStream);
  }

  @Override
  public void dispose() {
    try {
      detachProcessImpl();
    }
    finally {
      myTask = null;
      myDataHolder = null;
    }
  }
}
