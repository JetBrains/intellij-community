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