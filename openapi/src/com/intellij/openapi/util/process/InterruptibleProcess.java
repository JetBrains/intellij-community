/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.util.process;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public abstract class InterruptibleProcess extends InterruptibleActivity {
  private final Process myProcess;
  private InputStream myInputStream;
  private InputStream myErrorStream;
  private int myExitCode;

  protected InterruptibleProcess(final Process process, final long timeout, final TimeUnit timeUnit) {
    super(timeout, timeUnit);
    myProcess = process;

    myInputStream = new InputStreamWrapper(process.getInputStream());
    myErrorStream = new InputStreamWrapper(process.getErrorStream());
  }

  public final InputStream getErrorStream() {
    return myErrorStream;
  }

  public final InputStream getInputStream() {
    return myInputStream;
  }

  public int getExitCode() {
    return myExitCode;
  }

  protected void interrupt() {
    try {
      myInputStream.close();
      myErrorStream.close();
      myProcess.destroy();
    }
    catch (IOException e) {
      // Ignore
    }
  }

  protected void start() {
    try {
      myExitCode = myProcess.waitFor();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private class InputStreamWrapper extends InputStream {
    private InputStream myDelegate;

    public InputStreamWrapper(final InputStream delegate) {
      myDelegate = delegate;
    }

    public int read() throws IOException {
      int r = myDelegate.read();
      touch();
      return r;
    }

    public int read(final byte[] b) throws IOException {
      final int r = myDelegate.read(b);
      touch();
      return r;
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
      final int r = myDelegate.read(b, off, len);
      touch();
      return r;
    }
  }
}