// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JrtFileSystemImpl extends JrtFileSystem implements Disposable {
  private final Map<String, ArchiveHandler> myHandlers = Collections.synchronizedMap(CollectionFactory.createFilePathMap());
  private final AtomicBoolean mySubscribed = new AtomicBoolean(false);

  @Override
  public void dispose() {
    myHandlers.forEach((k, handler) -> handler.clearCaches());
    myHandlers.clear();
  }

  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  protected @Nullable String normalize(@NotNull String path) {
    var separatorIndex = path.indexOf(SEPARATOR);
    return separatorIndex > 0 ? FileUtil.normalize(path.substring(0, separatorIndex)) + path.substring(separatorIndex) : null;
  }

  @Override
  protected @NotNull String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, SEPARATOR);
  }

  @Override
  protected @NotNull String composeRootPath(@NotNull String localPath) {
    return localPath + SEPARATOR;
  }

  @Override
  protected @NotNull String extractRootPath(@NotNull String normalizedPath) {
    var separatorIndex = normalizedPath.indexOf(SEPARATOR);
    return separatorIndex > 0 ? normalizedPath.substring(0, separatorIndex + SEPARATOR.length()) : "";
  }

  @Override
  protected @NotNull ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    checkSubscription();

    var homePath = extractLocalPath(VfsUtilCore.getRootFile(entryFile).getPath());
    return myHandlers.computeIfAbsent(homePath, key -> {
      var handler = new JrtHandler(key);
      loadReleaseFileIntoVfs(key);
      return handler;
    });
  }

  private static void loadReleaseFileIntoVfs(String homePath) {
    var releasePath = homePath + "/release";
    VfsImplUtil.refreshAndFindFileByPath(LocalFileSystem.getInstance(), releasePath, file -> {
      if (file == null) {
        Logger.getInstance(JrtFileSystemImpl.class).warn("Cannot load into VFS: " + releasePath);
      }
    });
  }

  private void checkSubscription() {
    if (mySubscribed.getAndSet(true)) return;

    var app = ApplicationManager.getApplication();
    if (app.isDisposed()) return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
    //noinspection IncorrectParentDisposable
    Disposer.register(app, this);
    app.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        Set<VirtualFile> toRefresh = null;

        for (var e : events) {
          if (e.getFileSystem() instanceof LocalFileSystem) {
            String homePath = null;

            if (e instanceof VFileContentChangeEvent cce) {
              var file = cce.getFile();
              if ("release".equals(file.getName())) {
                homePath = file.getParent().getPath();
              }
            }
            else if (e instanceof VFileDeleteEvent de) {
              homePath = de.getFile().getPath();
            }

            if (homePath != null) {
              var handler = myHandlers.remove(homePath);
              if (handler != null) {
                handler.clearCaches();
                var root = findFileByPath(composeRootPath(homePath));
                if (root != null) {
                  ((NewVirtualFile)root).markDirtyRecursively();
                  if (toRefresh == null) toRefresh = new HashSet<>();
                  toRefresh.add(root);
                }
              }
            }
          }
        }

        if (toRefresh != null) {
          var synchronous = ApplicationManager.getApplication().isUnitTestMode();
          RefreshQueue.getInstance().refresh(!synchronous, true, null, toRefresh);
        }
      }
    });
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @Override
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    var path = local.toNioPath();
    return JdkUtil.isModularRuntime(path) && !JdkUtil.isExplodedModularRuntime(path);
  }

  @TestOnly
  public void release(@NotNull String localPath) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) throw new IllegalStateException();
    var handler = myHandlers.remove(localPath);
    if (handler == null) throw new IllegalArgumentException(localPath + " not in " + myHandlers.keySet());
    handler.clearCaches();
  }
}
