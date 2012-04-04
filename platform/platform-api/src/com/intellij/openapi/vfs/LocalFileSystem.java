/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.Processor;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public abstract class LocalFileSystem extends NewVirtualFileSystem {
  @NonNls public static final String PROTOCOL = "file";
  @NonNls public static final String PROTOCOL_PREFIX = PROTOCOL + "://";

  @SuppressWarnings("UtilityClassWithoutPrivateConstructor")
  private static class LocalFileSystemHolder {
    private static final LocalFileSystem ourInstance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public static LocalFileSystem getInstance() {
    return LocalFileSystemHolder.ourInstance;
  }

  /**
   * Checks whether given file is a symbolic link.
   *
   * @param file a file to check.
   * @return <code>true</code> if the file is a symbolic link, <code>false</code> otherwise
   * @since 11.0
   */
  @Override
  public boolean isSymLink(@NotNull final VirtualFile file) {
    return false;
  }

  /**
   * Checks whether given file is a special file.
   *
   * @param file a file to check.
   * @return <code>true</code> if the file exists and is a special one, <code>false</code> otherwise
   * @since 11.0
   */
  @Override
  public boolean isSpecialFile(@NotNull final VirtualFile file) {
    return false;
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile findFileByIoFile(@NotNull IFile file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull IFile ioFile);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   * @since 6.0
   */
  public abstract void refreshIoFiles(@NotNull Iterable<File> files);

  public abstract void refreshIoFiles(@NotNull Iterable<File> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /**
   * Performs a non-recursive synchronous refresh of specified files.
   *
   * @param files files to refresh.
   * @since 6.0
   */
  public abstract void refreshFiles(@NotNull Iterable<VirtualFile> files);

  public abstract void refreshFiles(@NotNull Iterable<VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish);

  /** @deprecated use {@linkplain com.intellij.openapi.vfs.VirtualFile#contentsToByteArray()} (to remove in IDEA 13) */
  @SuppressWarnings({"MethodMayBeStatic", "UnusedDeclaration"})
  public byte[] physicalContentsToByteArray(@NotNull VirtualFile virtualFile) throws IOException{
    return virtualFile.contentsToByteArray();
  }

  /** @deprecated use {@linkplain VirtualFile#getLength()} (to remove in IDEA 13) */
  @SuppressWarnings({"MethodMayBeStatic", "UnusedDeclaration"})
  public long physicalLength(@NotNull VirtualFile virtualFile) throws IOException{
    return virtualFile.getLength();
  }

  public interface WatchRequest {
    @NotNull
    String getRootPath();

    boolean isToWatchRecursively();

    /** @deprecated implementation details (to remove in IDEA 13) */
    @SuppressWarnings({"UnusedDeclaration"})
    @NotNull
    String getFileSystemRootPath();

    /** @deprecated implementation details (to remove in IDEA 13) */
    @SuppressWarnings({"UnusedDeclaration"})
    boolean dominates(@NotNull WatchRequest other);
  }

  @Nullable
  public abstract WatchRequest addRootToWatch(@NotNull final String rootPath, final boolean toWatchRecursively);

  @NotNull
  public abstract Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean toWatchRecursively);

  public abstract void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch);

  public abstract void removeWatchedRoot(@NotNull final WatchRequest watchRequest);

  public abstract void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  public abstract void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler);

  public abstract boolean processCachedFilesInSubtree(@NotNull VirtualFile file, @NotNull Processor<VirtualFile> processor);
}
