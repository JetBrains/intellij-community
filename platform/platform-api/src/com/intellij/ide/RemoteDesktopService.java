// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.SystemInfoRt;

import java.util.function.Supplier;

public abstract class RemoteDesktopService {
  private static final Supplier<RemoteDesktopService> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    return ApplicationManager.getApplication().getService(RemoteDesktopService.class);
  });

  public static RemoteDesktopService getInstance() {
    return ourInstance.get();
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
