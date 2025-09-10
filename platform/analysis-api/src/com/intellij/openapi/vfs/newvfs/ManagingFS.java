// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Function;

public abstract class ManagingFS implements FileSystemInterface {
  private static ManagingFS ourInstance;

  public static ManagingFS getInstance() {
    ManagingFS instance = ourInstance;
    if (instance == null) {
      instance = ApplicationManager.getApplication().getService(ManagingFS.class);
      ourInstance = instance;
    }
    return instance;
  }

  public static ManagingFS getInstanceOrNull() {
    return ourInstance;
  }

  static {
    ApplicationManager.registerCleaner(() -> ourInstance = null);
  }

  public abstract @Nullable AttributeInputStream readAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att);

  public abstract @NotNull AttributeOutputStream writeAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att);

  /**
   * @return a number that's incremented every time something changes for the file: name, size, flags, content.
   * This number has persisted between IDE sessions and so it'll always increase.
   * This method invocation means disk access, so it's not terribly cheap.
   * @deprecated to be dropped as there is no real use for it
   */
  //FIXME RC: drop this method from API -- the only use is in test code
  @Deprecated(forRemoval = true)
  public abstract int getModificationCount(@NotNull VirtualFile fileOrDirectory);

  /**
   * @return a number that's incremented every time something changes in the VFS structure, i.e. file hierarchy or names.
   * This only counts modifications done in the current IDE session.
   */
  public abstract int getStructureModificationCount();

  /**
   * @return a number that's incremented every time modification count for some file is advanced, @see {@link #getModificationCount(VirtualFile)}.
   * This number has persisted between IDE sessions and so it'll always increase.
   */
  @TestOnly
  public abstract int getFilesystemModificationCount();

  public abstract long getCreationTimestamp();

  /**
   * @return true if VFS already has fetched (and cached) _all_ dir's children from apt {@link com.intellij.openapi.vfs.VirtualFileSystem}
   * It is not guaranteed that cached children == actual children at the moment: updates from the underling FS could be applied with delay
   */
  public abstract boolean areChildrenLoaded(@NotNull VirtualFile dir);

  /** @return true if VFS already fetches and caches at least _some_ children from apt {@link com.intellij.openapi.vfs.VirtualFileSystem} */
  public abstract boolean wereChildrenAccessed(@NotNull VirtualFile dir);

  public abstract @Nullable NewVirtualFile findRoot(@NotNull String path, @NotNull NewVirtualFileSystem fs);

  public abstract VirtualFile @NotNull [] getRoots();

  public abstract VirtualFile @NotNull [] getRoots(@NotNull NewVirtualFileSystem fs);

  public abstract VirtualFile @NotNull [] getLocalRoots();

  public abstract @Nullable VirtualFile findFileById(int id);

  @ApiStatus.Internal
  protected abstract @NotNull <P, R> Function<P, R> accessDiskWithCheckCanceled(Function<? super P, ? extends R> function);
}
