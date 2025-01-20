// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import java.security.Permission;

/** @deprecated <not needed anymore; kept for compatibility with pre-251 DevKit versions */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public class CoreBootstrapSecurityManager extends SecurityManager {
  @Override
  public void checkPermission(Permission permission) { }
}
