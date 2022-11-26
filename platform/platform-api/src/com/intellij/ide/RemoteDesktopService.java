// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.SystemInfoRt;

public abstract class RemoteDesktopService {
  private static volatile RemoteDesktopService ourInstance = CachedSingletonsRegistry.markCachedField(RemoteDesktopService.class);

  public static RemoteDesktopService getInstance() {
    RemoteDesktopService service = ourInstance;
    if (service == null) {
      ourInstance = service = ApplicationManager.getApplication().getService(RemoteDesktopService.class);
    }
    return service;
  }

  public static boolean isRemoteSession() {
    if (!SystemInfoRt.isWindows) {
      return false;
    }
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred() || ApplicationManager.getApplication() == null) {
      return false;
    }
    RemoteDesktopService instance = getInstance();
    return instance != null && instance.isRemoteDesktopConnected();
  }

  public abstract boolean isRemoteDesktopConnected();
}
