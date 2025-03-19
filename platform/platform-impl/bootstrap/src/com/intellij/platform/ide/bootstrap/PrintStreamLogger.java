// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;

final class PrintStreamLogger extends PrintStream {
  PrintStreamLogger(String name, PrintStream originalStream) {
    super(new OutputStreamLogger(name, originalStream), true);
  }
}

final class OutputStreamLogger extends OutputStream {
  // IJPL-157959
  // If we redirected output streams to the logger and it happens that
  // some of the logging handlers falls into error it tries to report the error
  // via its ErrorManager which in turn prints the error into stderr that causes stofl.
  // here we try to wrap ErrorManager to avoid this recursion
  private static class SkipRedirectErrorManager extends ErrorManager {
    private final ErrorManager myOriginal;

    private SkipRedirectErrorManager(ErrorManager original) {
      myOriginal = original;
    }

    @Override
    public synchronized void error(String msg, Exception ex, int code) {
      ourSkipRedirect.set(true);
      try {
        myOriginal.error(msg, ex, code);
      }
      finally {
        ourSkipRedirect.set(false);
      }
    }
  }

  private static final ThreadLocal<Boolean> ourSkipRedirect = ThreadLocal.withInitial(() -> false);

  private static final int BUFFER_SIZE = 10000;
  private final byte[] myBuffer = new byte[BUFFER_SIZE];
  private final PrintStream myOriginalStream;
  @SuppressWarnings("NonConstantLogger") private final Logger myLogger;
  private int myPosition;
  private boolean mySlashRWritten;

  OutputStreamLogger(String name, PrintStream originalStream) {
    myOriginalStream = originalStream;
    myLogger = Logger.getInstance(name);
    // wrap ErrorManager of each handler to avoid recursion check
    var rootLogger = java.util.logging.Logger.getLogger("");
    // it may happen that a faulting handler is added later but we hope that doesn't happen in our case
    for (Handler handler : rootLogger.getHandlers()) {
      ErrorManager errorManager = handler.getErrorManager();
      if (errorManager instanceof SkipRedirectErrorManager) continue;
      handler.setErrorManager(new SkipRedirectErrorManager(errorManager));
    }
  }

  @Override
  public void write(int b) {
    myOriginalStream.write(b);
    if (ourSkipRedirect.get()) return;
    processByte(b);
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) {
    myOriginalStream.write(b, off, len);
    if (ourSkipRedirect.get()) return;
    for (int i = 0; i < len; i++) {
      processByte(b[off + i]);
    }
  }

  private synchronized void processByte(int b) {
    if (b == '\r') {
      writeBuffer();
      mySlashRWritten = true;
    }
    else if (b == '\n') {
      if (mySlashRWritten) {
        mySlashRWritten = false;
      }
      else {
        writeBuffer();
      }
    }
    else {
      mySlashRWritten = false;
      if (myPosition == BUFFER_SIZE) {
        writeBuffer();
      }
      myBuffer[myPosition++] = (byte)b;
    }
  }

  private final AtomicBoolean myStackOverflowProtectionTriggered = new AtomicBoolean();
  private final ThreadLocal<Boolean> myStackOverflowProtection = ThreadLocal.withInitial(() -> false);

  private void writeBuffer() {
    if (myStackOverflowProtection.get()) {
      if (myStackOverflowProtectionTriggered.compareAndSet(false, true)) {
        myLogger.error("Stack overflow protection triggered in logger. A standard stream overridden to write to logs?");
      }

      return;
    }

    myStackOverflowProtection.set(true);
    try {
      myLogger.info(new String(myBuffer, 0, myPosition, Charset.defaultCharset()));
      myPosition = 0;
    } finally {
      myStackOverflowProtection.set(false);
    }
  }
}
