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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class NewVirtualFileSystem extends VirtualFileSystem implements FileSystemInterface, CachingVirtualFileSystem {
  private final Map<VirtualFileListener, VirtualFileListener> myListenerWrappers = new HashMap<VirtualFileListener, VirtualFileListener>();

  public abstract boolean isCaseSensitive();

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

  @Override
  public boolean isSpecialFile(@NotNull final VirtualFile file) {
    return false;
  }

  protected abstract String extractRootPath(@NotNull String path);

  @Override
  public void addVirtualFileListener(@NotNull final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
      VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
      myListenerWrappers.put(listener, wrapper);
    }
  }

  @Override
  public void removeVirtualFileListener(@NotNull final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      final VirtualFileListener wrapper = myListenerWrappers.remove(listener);
      if (wrapper != null) {
        VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
      }
    }
  }

  public abstract int getRank();

  @Override
  public abstract VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException;
  @Override
  @NotNull
  public abstract VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException;
  @Override
  public abstract VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException;
  @Override
  public abstract void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException;
  @Override
  public abstract void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException;
  @Override
  public abstract void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }

  public String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  /**
   * Queries the file about several attributes at once, and returns them ORed together.
   * This method is typically faster than several methods calls querying corresponding file attributes one by one.
   *
   * @param file  to query.
   * @param flags Attributes to query the file for.
   *              Each attribute is an int constant from this class.
   *              Following attributes are defined:
   *              <ul>
   *              <li>{@link com.intellij.openapi.util.io.FileUtil#BA_EXISTS} is set if {@link java.io.File#exists()} returns true</li>
   *              <li>{@link com.intellij.openapi.util.io.FileUtil#BA_DIRECTORY} is set if {@link java.io.File#isDirectory()} returns true</li>
   *              <li>{@link com.intellij.openapi.util.io.FileUtil#BA_HIDDEN} is set if {@link java.io.File#isHidden()} returns true</li>
   *              <li>{@link com.intellij.openapi.util.io.FileUtil#BA_REGULAR} is set if {@link java.io.File#isFile()} returns true</li>
   *              </ul>
   *              Attributes can be bitwise ORed together to query several file attributes at once.
   *              <code>-1</code> as an argument value will query all attributes.
   * @return Attributes mask for the file, where the bit is set if the corresponding attribute for the file is true.
   *         That is, the return value is <pre>{@code
   *           (file.exists() ? BA_EXISTS : 0) |
   *           (file.isDirectory()() ? BA_DIRECTORY : 0) |
   *           (file.isRegular()() ? BA_REGULAR : 0) |
   *           (file.isHidden()() ? BA_HIDDEN : 0)
   *           }</pre>
   *         Except that the bit in the return value is undefined if the corresponding bit in the flags parameter is not set.
   *  <p>
   *  Example usage:
   *  <pre>{@code
   *  int attributes = getBooleanAttributes(file, BA_EXISTS | BA_DIRECTORY);
   *  if ((attributes & BA_EXISTS) != 0) {
   *    // file exists
   *    boolean isDirectory = (attributes & BA_DIRECTORY) != 0;
   *  }}</pre>
   */
  @FileUtil.FileBooleanAttributes
  public abstract int getBooleanAttributes(@NotNull final VirtualFile file, @FileUtil.FileBooleanAttributes int flags);
}
