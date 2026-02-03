// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BinaryOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

public class BinaryOSProcessHandler extends OSProcessHandler {
  private final BufferExposingByteArrayOutputStream myOutput = new BufferExposingByteArrayOutputStream();

  public BinaryOSProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  public BinaryOSProcessHandler(@NotNull Process process, @NotNull String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  public byte @NotNull [] getOutput() {
    return myOutput.toByteArray();
  }

  @Override
  protected @NotNull BaseDataReader createOutputDataReader() {
    return new SimpleBinaryReader(myProcess.getInputStream(), readerOptions().policy());
  }

  private String getPresentableName() {
    return myPresentableName;
  }
  
  private final class SimpleBinaryReader extends BinaryOutputReader {
    private SimpleBinaryReader(InputStream stream, SleepingPolicy policy) {
      super(stream, policy);
      start("output stream of " + getPresentableName());
    }

    @Override
    protected void onBinaryAvailable(byte @NotNull [] data, int size) {
      myOutput.write(data, 0, size);
    }

    @Override
    protected @NotNull Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return BinaryOSProcessHandler.this.executeTask(runnable);
    }
  }
}