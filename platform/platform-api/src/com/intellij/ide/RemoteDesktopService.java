// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.SystemInfo;

public abstract class RemoteDesktopService {
  private static volatile RemoteDesktopService ourInstance;

  public static RemoteDesktopService getInstance() {
    RemoteDesktopService service = ourInstance;
    if (service == null) {
      ourInstance = service = ServiceManager.getService(RemoteDesktopService.class);
    }
    return service;
  }

  public static boolean isRemoteSession() {
    if (!SystemInfo.isWindows) return false;
    return ApplicationManager.getApplication() != null && getInstance().isRemoteDesktopConnected();
  }

  public abstract boolean isRemoteDesktopConnected();
}
