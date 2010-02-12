/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class NewVirtualFileSystem extends VirtualFileSystem implements FileSystemInterface {
  @NonNls private static final String FILE_SEPARATORS = "/" + File.separator;
  private final Map<VirtualFileListener, VirtualFileListener> myListenerWrappers = new HashMap<VirtualFileListener, VirtualFileListener>();

  public abstract boolean isCaseSensitive();

  @Nullable
  public VirtualFile findFileByPath(@NotNull @NonNls final String path) {
    final String normalizedPath = normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, this);
    if (file == null || !file.exists()) return null;

    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.length() == 0 || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls final String path) {
    final String normalizedPath = normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, this);
    if (file == null || !file.exists()) return null;

    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.length() == 0 || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChildIfCached(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull final String path) {
    final String normalizedPath = normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, this);
    if (file == null || !file.exists()) return null;

    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.length() == 0 || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.refreshAndFindChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  protected String normalize(final String path) {
    return path;
  }

  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    refresh(asynchronous);
  }

  public void refresh(final boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }

  public boolean isReadOnly() {
    return true;
  }

  protected abstract String extractRootPath(@NotNull String path);

  public void addVirtualFileListener(@NotNull final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
      VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
      myListenerWrappers.put(listener, wrapper);
    }
  }

  public void removeVirtualFileListener(@NotNull final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      final VirtualFileListener wrapper = myListenerWrappers.remove(listener);
      if (wrapper != null) {
        VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
      }
    }
  }

  public abstract int getRank();

  public abstract VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException;
  @NotNull
  public abstract VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException;
  public abstract VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException;
  public abstract void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException;
  public abstract void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException;
  public abstract void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }

  public String getCanonicallyCasedName(VirtualFile file) {
    return file.getName();
  }
}
