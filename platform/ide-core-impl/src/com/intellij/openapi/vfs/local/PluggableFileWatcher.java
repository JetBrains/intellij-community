// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.List;

/**
 * @author dslomov
 */
public abstract class PluggableFileWatcher {
  public static final ExtensionPointName<PluggableFileWatcher> EP_NAME =
    ExtensionPointName.create("com.intellij.vfs.local.pluggableFileWatcher");

  public abstract void initialize(@NotNull ManagingFS managingFS, @NotNull FileWatcherNotificationSink notificationSink);

  public abstract void dispose();

  public abstract boolean isOperational();

  public abstract boolean isSettingRoots();

  /**
   * The inputs to this method must be absolute and free of symbolic links.
   */
  public abstract void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat);

  public void resetChangedPaths() { }

  @TestOnly
  public abstract void startup() throws IOException;

  @TestOnly
  public abstract void shutdown() throws InterruptedException;
}
