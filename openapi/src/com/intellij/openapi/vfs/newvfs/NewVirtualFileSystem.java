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
  public VirtualFile refreshAndFindFileByPath(final String path) {
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

  public void addVirtualFileListener(final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
      VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
      myListenerWrappers.put(listener, wrapper);
    }
  }

  public void removeVirtualFileListener(final VirtualFileListener listener) {
    synchronized (myListenerWrappers) {
      final VirtualFileListener wrapper = myListenerWrappers.remove(listener);
      if (wrapper != null) {
        VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
      }
    }
  }

  public abstract int getRank();

  public abstract VirtualFile copyFile(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String copyName) throws IOException;
  public abstract VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException;
  public abstract VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException;
  public abstract void deleteFile(final Object requestor, final VirtualFile file) throws IOException;
  public abstract void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException;
  public abstract void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }
}