// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.User32Ex;

final class RemoteDesktopDetector extends RemoteDesktopService {
  private volatile boolean myFailureDetected;
  private volatile boolean myRemoteDesktopConnected;

  private RemoteDesktopDetector() {
    if (SystemInfoRt.isWindows) {
      DisplayChangeDetector.getInstance().addListener(this::updateState);
      updateState();
    }
  }

  private void updateState() {
    if (!myFailureDetected) {
      try {
        // This might not work in all cases, but hopefully is a more reliable method than the current one (checking for font smoothing)
        // see https://msdn.microsoft.com/en-us/library/aa380798%28v=vs.85%29.aspx
        myRemoteDesktopConnected = User32Ex.getSystemMetrics(User32Ex.SM_REMOTESESSION) != 0;
        Logger.getInstance(RemoteDesktopDetector.class).debug("Detected remote desktop: ", myRemoteDesktopConnected);
      }
      catch (Throwable e) {
        myRemoteDesktopConnected = false;
        myFailureDetected = true;
        Logger.getInstance(RemoteDesktopDetector.class).warn("Error while calling GetSystemMetrics", e);
      }
    }
  }

  @Override
  public boolean isRemoteDesktopConnected() {
    return myRemoteDesktopConnected;
  }
}