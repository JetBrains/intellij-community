// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.projectRoots.JdkUtil;
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

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JrtFileSystemImpl extends JrtFileSystem {
  private final Map<String, ArchiveHandler> myHandlers = Collections.synchronizedMap(CollectionFactory.createFilePathMap());
  private final AtomicBoolean mySubscribed = new AtomicBoolean(false);

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  @Override
  protected String normalize(@NotNull String path) {
    int separatorIndex = path.indexOf(SEPARATOR);
    return separatorIndex > 0 ? FileUtil.normalize(path.substring(0, separatorIndex)) + path.substring(separatorIndex) : null;
  }

  @NotNull
  @Override
  protected String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, SEPARATOR);
  }

  @NotNull
  @Override
  protected String composeRootPath(@NotNull String localPath) {
    return localPath + SEPARATOR;
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    int separatorIndex = normalizedPath.indexOf(SEPARATOR);
    return separatorIndex > 0 ? normalizedPath.substring(0, separatorIndex + SEPARATOR.length()) : "";
  }

  @NotNull
  @Override
  protected ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    checkSubscription();

    String homePath = extractLocalPath(VfsUtilCore.getRootFile(entryFile).getPath());
    return myHandlers.computeIfAbsent(homePath, key -> {
      JrtHandler handler = new JrtHandler(key);
      ApplicationManager.getApplication().invokeLater(
        () -> LocalFileSystem.getInstance().refreshAndFindFileByPath(key + "/release"),
        ModalityState.defaultModalityState());
      return handler;
    });
  }

  private void checkSubscription() {
    if (mySubscribed.getAndSet(true)) return;

    Application app = ApplicationManager.getApplication();
    if (app.isDisposed()) return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
    app.getMessageBus().connect(app).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Set<VirtualFile> toRefresh = null;

        for (VFileEvent e : events) {
          if (e.getFileSystem() instanceof LocalFileSystem) {
            String homePath = null;

            if (e instanceof VFileContentChangeEvent) {
              VirtualFile file = ((VFileContentChangeEvent)e).getFile();
              if ("release".equals(file.getName())) {
                homePath = file.getParent().getPath();
              }
            }
            else if (e instanceof VFileDeleteEvent) {
              homePath = ((VFileDeleteEvent)e).getFile().getPath();
            }

            if (homePath != null) {
              ArchiveHandler handler = myHandlers.remove(homePath);
              if (handler != null) {
                handler.dispose();
                VirtualFile root = findFileByPath(composeRootPath(homePath));
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
          boolean async = !ApplicationManager.getApplication().isUnitTestMode();
          RefreshQueue.getInstance().refresh(async, true, null, toRefresh);
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
    Path path = local.toNioPath();
    return JdkUtil.isModularRuntime(path) && !JdkUtil.isExplodedModularRuntime(path);
  }

  @TestOnly
  public void release(@NotNull String localPath) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) throw new IllegalStateException();
    ArchiveHandler handler = myHandlers.remove(localPath);
    if (handler == null) throw new IllegalArgumentException(localPath + " not in " + myHandlers.keySet());
    handler.dispose();
  }
}
