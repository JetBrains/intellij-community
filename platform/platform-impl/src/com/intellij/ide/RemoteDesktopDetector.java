// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.User32Ex;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

final class RemoteDesktopDetector extends RemoteDesktopService {
  private static final Logger LOG = Logger.getInstance(RemoteDesktopDetector.class);

  private final AtomicBoolean myRemoteDesktopConnected = new AtomicBoolean();
  private volatile boolean myFailureDetected;

  private RemoteDesktopDetector() {
    if (SystemInfoRt.isWindows) {
      // the initial read must finish first, or it can overwrite the result of a concurrent updateState
      myRemoteDesktopConnected.set(readRemoteDesktopConnected());
      DisplayChangeDetector.getInstance().addListener(this::updateState);
    }
  }

  @Override
  public boolean isRemoteDesktopConnected() {
    return myRemoteDesktopConnected.get();
  }

  private void updateState() {
    boolean connected = readRemoteDesktopConnected();
    // succeeds once per change, even with several callers
    if (myRemoteDesktopConnected.compareAndSet(!connected, connected)) {
      fireRemoteSessionChanged();
    }
  }

  /** @return the current state, or the last known state after a failed native call */
  private boolean readRemoteDesktopConnected() {
    if (myFailureDetected) {
      return myRemoteDesktopConnected.get();
    }
    try {
      // This might not work in all cases, but hopefully is a more reliable method than the current one (checking for font smoothing)
      // see https://msdn.microsoft.com/en-us/library/aa380798%28v=vs.85%29.aspx
      boolean connected = User32Ex.getSystemMetrics(User32Ex.SM_REMOTESESSION) != 0;
      LOG.debug("Detected remote desktop: ", connected);
      return connected;
    }
    catch (Throwable e) {
      rethrowControlFlowException(e);
      myFailureDetected = true;
      LOG.warn("Error while calling GetSystemMetrics", e);
      return false;
    }
  }
}
