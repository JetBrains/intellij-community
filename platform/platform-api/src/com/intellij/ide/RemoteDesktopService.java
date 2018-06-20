// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;

public abstract class RemoteDesktopService {
  public static RemoteDesktopService getInstance() {
    return ServiceManager.getService(RemoteDesktopService.class);
  }

  public static boolean isRemoteSession() {
    return ApplicationManager.getApplication() != null && getInstance().isRemoteDesktopConnected();
  }

  public abstract boolean isRemoteDesktopConnected();
}
