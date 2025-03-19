// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import java.security.Permission;
import java.util.concurrent.atomic.AtomicBoolean;

/** @deprecated <not needed anymore; kept for compatibility with pre-251 DevKit versions */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public class CoreBootstrapSecurityManager extends SecurityManager {
  private final AtomicBoolean initialised = new AtomicBoolean(false);

  @Override
  public void checkPermission(Permission permission) {
    if (initialised.compareAndSet(false, true)) {
      System.clearProperty("java.security.manager");
      System.setSecurityManager(null);
    }
  }
}
