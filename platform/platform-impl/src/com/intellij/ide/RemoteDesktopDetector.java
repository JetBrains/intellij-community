// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.platform.win32.User32;

final class RemoteDesktopDetector extends RemoteDesktopService {
  private volatile boolean myFailureDetected;
  private volatile boolean myRemoteDesktopConnected;

  private RemoteDesktopDetector() {
    if (SystemInfo.isWindows && JnaLoader.isLoaded()) {
      DisplayChangeDetector.getInstance().addListener(this::updateState);
      updateState();
    }
  }

  private void updateState() {
    if (!myFailureDetected) {
      try {
        // This might not work in all cases, but hopefully is a more reliable method than the current one (checking for font smoothing)
        // see https://msdn.microsoft.com/en-us/library/aa380798%28v=vs.85%29.aspx
        myRemoteDesktopConnected = User32.INSTANCE.GetSystemMetrics(0x1000) != 0; // 0x1000 is SM_REMOTESESSION
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