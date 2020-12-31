// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.notification.NotificationListener;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author dslomov
 */
public interface FileWatcherNotificationSink {
  /** @deprecated please call {@link #notifyManualWatchRoots(PluggableFileWatcher, Collection)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @SuppressWarnings({"DeprecatedIsStillUsed", "RedundantSuppression"})
  void notifyManualWatchRoots(@NotNull Collection<String> roots);

  default void notifyManualWatchRoots(@NotNull PluggableFileWatcher watcher, @NotNull Collection<String> roots) {
    notifyManualWatchRoots(roots);
  }

  void notifyMapping(@NotNull Collection<? extends Pair<String, String>> mapping);

  void notifyDirtyPath(@NotNull String path);

  void notifyPathCreatedOrDeleted(@NotNull String path);

  void notifyDirtyDirectory(@NotNull String path);

  void notifyDirtyPathRecursive(@NotNull String path);

  void notifyReset(@Nullable String path);

  void notifyUserOnFailure(@NotNull @NlsContexts.NotificationContent String cause, @Nullable NotificationListener listener);
}
