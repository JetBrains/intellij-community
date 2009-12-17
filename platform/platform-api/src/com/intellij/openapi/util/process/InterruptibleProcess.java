/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public abstract class InterruptibleProcess extends InterruptibleActivity {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.process.InterruptibleProcess");
  private final Process myProcess;
  private final InputStream myInputStream;
  private final InputStream myErrorStream;
  private int myExitCode;
  private boolean myDestroyed;

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
    closeProcess();
  }

  public static void close(final Process process) {
    ProcessCloseUtil.close(process);
  }

  public void closeProcess() {
    if (myDestroyed) return;
    myDestroyed = true;
    close(myProcess);
  }

  protected void start() {
    try {
      myExitCode = myProcess.waitFor();
    }
    catch (InterruptedException e) {
      LOG.debug(e);
    }
  }

  private class InputStreamWrapper extends InputStream {
    private final InputStream myDelegate;

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

    @Override
    public void close() throws IOException {
      myDelegate.close();
    }
  }
}
