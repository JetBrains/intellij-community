// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import java.nio.file.FileSystems;
import java.security.Permission;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Used to initialize default file system before the system class loader initialization.
 * WARNING: there are plans for possible removal of Security Manager in future release - https://openjdk.org/jeps/411.
 *
 * @see com.intellij.util.lang.PathClassLoader
 * @see CoreRoutingFileSystemProvider
 * @see FileSystems#getDefault()
 */
public class CoreBootstrapSecurityManager extends SecurityManager {
  private final AtomicBoolean initialised = new AtomicBoolean(false);

  @Override
  public void checkPermission(Permission permission) {
    if (initialised.compareAndSet(false, true)) {
      // force early initialization of default file system to avoid `java.lang.ClassLoader#getSystemClassLoader` call during the system class loader instantiation.
      FileSystems.getDefault();
      System.clearProperty("java.security.manager");
      System.setSecurityManager(null);
    }
  }
}
