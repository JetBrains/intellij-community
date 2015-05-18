/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * @author max
 */
public abstract class NewVirtualFileSystem extends VirtualFileSystem implements FileSystemInterface, CachingVirtualFileSystem {
  private final Map<VirtualFileListener, VirtualFileListener> myListenerWrappers =
    ContainerUtil.newConcurrentMap();

  @Nullable
  public abstract VirtualFile findFileByPathIfCached(@NotNull @NonNls final String path);

  @Nullable
  protected String normalize(@NotNull String path) {
    return path;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    refresh(asynchronous);
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public boolean isSymLink(@NotNull final VirtualFile file) {
    return false;
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return null;
  }

  @NotNull
  protected abstract String extractRootPath(@NotNull String path);

  @Override
  public void addVirtualFileListener(@NotNull final VirtualFileListener listener) {
    VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
    VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
    myListenerWrappers.put(listener, wrapper);
  }

  @Override
  public void removeVirtualFileListener(@NotNull final VirtualFileListener listener) {
    final VirtualFileListener wrapper = myListenerWrappers.remove(listener);
    if (wrapper != null) {
      VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
    }
  }

  public abstract int getRank();

  @NotNull
  @Override
  public abstract VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException;

  @Override
  @NotNull
  public abstract VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException;

  @NotNull
  @Override
  public abstract VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException;

  @Override
  public abstract void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException;

  @Override
  public abstract void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException;

  @Override
  public abstract void renameFile(final Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }

  @NotNull
  public String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  /**
   * Reads various file attributes in one shot (to reduce the number of native I/O calls).
   *
   * @param file file to get attributes of.
   * @return attributes of a given file, or <code>null</code> if the file doesn't exist.
   * @since 11.1
   */
  @Nullable
  public abstract FileAttributes getAttributes(@NotNull VirtualFile file);
}
