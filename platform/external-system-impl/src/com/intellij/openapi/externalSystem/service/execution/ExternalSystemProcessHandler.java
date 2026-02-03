// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.SoftlyKillableProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.util.DiscardingInputStream;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;

/**
 * @author Vladislav.Soroka
 */
public class ExternalSystemProcessHandler extends BuildProcessHandler implements SoftlyKillableProcessHandler, Disposable {

  private static final Logger LOG = Logger.getInstance(ExternalSystemProcessHandler.class);
  static final Key<Boolean> SOFT_PROCESS_KILL_ENABLED_KEY = Key.create("SOFT_PROCESS_KILL_ENABLED_KEY");

  private final @NotNull String myExecutionName;
  private @Nullable ExternalSystemTask myTask;
  private @Nullable UserDataHolder myDataHolder;
  private @Nullable OutputStream myProcessInputWriter;
  private @Nullable InputStream myProcessInputReader;

  private final @NotNull AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();
  private boolean escapeAnsiText = true;

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

  public @Nullable ExternalSystemTask getTask() {
    return myTask;
  }

  @ApiStatus.Experimental
  public void disableAnsiTextEscaping() {
    escapeAnsiText = false;
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, final @NotNull Key outputType) {
    if (escapeAnsiText) {
      myAnsiEscapeDecoder.escapeText(text, outputType, (decodedText, attributes) ->
        super.notifyTextAvailable(decodedText, attributes)
      );
    }
    else {
      super.notifyTextAvailable(text, outputType);
    }
  }

  @Override
  @ApiStatus.Experimental
  public boolean shouldKillProcessSoftly() {
    return myDataHolder != null && myDataHolder.getUserData(SOFT_PROCESS_KILL_ENABLED_KEY) == Boolean.TRUE;
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

  @Override
  public @Nullable OutputStream getProcessInput() {
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
