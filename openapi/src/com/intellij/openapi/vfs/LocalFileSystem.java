/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.File;
import java.util.Set;
import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public abstract class LocalFileSystem extends VirtualFileSystem {
  public static final String PROTOCOL = "file";

  public static LocalFileSystem getInstance(){

    return ApplicationManager.getApplication().getComponent(LocalFileSystem.class);
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(File file);

  public interface WatchRequest {
    @NotNull VirtualFile getRoot();

    boolean isToWatchRecursively();
  }

  /**
   * Adds this rootFile as the watch root for file system
   * @param toWatchRecursively whether the whole subtree should be monitored
   * @return request handle or null if rootFile does not belong to this file system
   */
  @Nullable
  public abstract WatchRequest addRootToWatch(final @NotNull VirtualFile rootFile, final boolean toWatchRecursively);

  @NotNull
  public abstract Set<WatchRequest> addRootsToWatch(final @NotNull Collection<VirtualFile> rootFiles, final boolean toWatchRecursively);

  public abstract void removeWatchedRoots(final @NotNull Set<WatchRequest> rootsToWatch);

  public abstract void removeWatchedRoot(final @NotNull WatchRequest watchRequest);
}