// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;

public abstract class RemoteDesktopService {
  public static RemoteDesktopService getInstance() {
    return ServiceManager.getService(RemoteDesktopService.class);
  }

  public static boolean isRemoteSession() {
    return getInstance().isRemoteDesktopConnected();
  }

  public static boolean isAnimationDisabled() {
    return (!SystemInfo.isWin8OrNewer || Registry.is("animation.disabled.on.remote.desktop")) && isRemoteSession();
  }

  public abstract boolean isRemoteDesktopConnected();
}
