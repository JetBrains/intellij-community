// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class OutputWrapper extends OutputStream {

  private final @NotNull ExternalSystemTaskNotificationListener myListener;
  private final @NotNull ExternalSystemTaskId myTaskId;
  private @Nullable StringBuilder myBuffer;
  private final boolean myStdOut;

  public OutputWrapper(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull ExternalSystemTaskId taskId, boolean stdOut) {
    myListener = listener;
    myTaskId = taskId;
    myStdOut = stdOut;
  }

  @Override
  public synchronized void write(int b) {
    if (myBuffer == null) {
      myBuffer = new StringBuilder();
    }
    myBuffer.append((char)b);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    if (myBuffer == null) {
      myBuffer = new StringBuilder();
    }
    myBuffer.append(new String(b, off, len, StandardCharsets.UTF_8));
  }

  @Override
  public synchronized void flush() {
    doFlush();
  }

  private void doFlush() {
    if (myBuffer == null || myBuffer.isEmpty()) {
      return;
    }
    myListener.onTaskOutput(myTaskId, myBuffer.toString(), myStdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
    myBuffer.setLength(0);
  }
}
