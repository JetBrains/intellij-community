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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

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
      try {
        PipedInputStream inputStream = new MyPipedInputStream();
        myProcessInput = new MyPipedOutputStream(inputStream);
        ((UserDataHolder)task).putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, inputStream);
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
    ExternalSystemTask task = myTask;
    if (task instanceof UserDataHolder) {
      ((UserDataHolder)task).putUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY, null);
    }
    StreamUtil.closeStream(myProcessInput);
    myProcessInput = null;
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

  /**
   * @see <a href="http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4545831">JDK-4545831: PipedInputStream performance problems</a>
   * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8014239">JDK-8014239: PipedInputStream not notifying waiting readers on receive</a>
   */
  private static class MyPipedInputStream extends PipedInputStream {
    MyPipedInputStream() {
      super();
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
      try {
        return super.read(b, off, len);
      }
      finally {
        //noinspection SynchronizeOnThis
        notifyAll();
      }
    }
  }

  /**
   * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8014239">JDK-8014239: PipedInputStream not notifying waiting readers on receive</a>
   */
  private static class MyPipedOutputStream extends PipedOutputStream {
    MyPipedOutputStream(PipedInputStream snk) throws IOException {
      super(snk);
    }

    @Override
    public void write(int b) throws IOException {
      super.write(b);
      flush();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      super.write(b, off, len);
      flush();
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
      write(b, 0, b.length);
    }
  }
}
