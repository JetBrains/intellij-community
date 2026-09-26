// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.List;

public abstract class PluggableFileWatcher {
  public static final ExtensionPointName<PluggableFileWatcher> EP_NAME =
    ExtensionPointName.create("com.intellij.vfs.local.pluggableFileWatcher");

  public abstract void initialize(@NotNull FileWatcherNotificationSink notificationSink);

  public abstract void dispose();

  /// The method should return `true` if the watcher is ready to receive [#setWatchRoots] requests
  /// (e.g., if the watcher depends on some external helper, the latter should be present and possibly running).
  ///
  /// @implNote the method is expected to be fast; perform potentially long tasks in [#initialize] or [#setWatchRoots].
  public abstract boolean isOperational();

  public abstract boolean isSettingRoots();

  /// The inputs to this method must be absolute and free of symbolic links.
  ///
  /// @implNote An implementation **must report** paths it doesn't recognize via [FileWatcherNotificationSink#notifyManualWatchRoots].
  /// On application shutdown, the corresponding parameter is set to `true` and the lists are empty; in this case,
  /// implementations should minimize their operations to speed up shutdown.
  public abstract void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat, boolean shuttingDown);

  public void resetChangedPaths() { }

  @TestOnly
  public abstract void startup() throws IOException;

  @TestOnly
  public abstract void shutdown() throws InterruptedException;
}
