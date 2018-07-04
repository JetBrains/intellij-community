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

  @NotNull
  public byte[] getOutput() {
    return myOutput.toByteArray();
  }

  @NotNull
  @Override
  protected BaseDataReader createOutputDataReader() {
    return new SimpleBinaryReader(myProcess.getInputStream(), readerOptions().policy());
  }

  private class SimpleBinaryReader extends BinaryOutputReader {
    private SimpleBinaryReader(InputStream stream, SleepingPolicy policy) {
      super(stream, policy);
      start("output stream of " + myPresentableName);
    }

    @Override
    protected void onBinaryAvailable(@NotNull byte[] data, int size) {
      myOutput.write(data, 0, size);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return BinaryOSProcessHandler.this.executeTask(runnable);
    }
  }
}