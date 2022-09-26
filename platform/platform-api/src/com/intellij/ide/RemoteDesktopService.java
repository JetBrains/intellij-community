// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.SystemInfo;

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
    if (!SystemInfo.isWindows) return false;
    if (ApplicationManager.getApplication() == null || !LoadingState.COMPONENTS_REGISTERED.isOccurred()) return false;
    RemoteDesktopService instance = getInstance();
    return instance != null && instance.isRemoteDesktopConnected();
  }

  public abstract boolean isRemoteDesktopConnected();
}
