// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * @see MultiRoutingFileSystemProvider
 * @see FileSystems#getDefault()
 * @deprecated <a href="https://bugs.openjdk.org/browse/JDK-8338411">Security Manager has been disabled in java 24</a>
 */
@Deprecated(forRemoval = true)
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
