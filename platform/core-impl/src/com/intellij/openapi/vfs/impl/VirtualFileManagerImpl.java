/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.CachingVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class VirtualFileManagerImpl extends VirtualFileManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFileManagerImpl");

  private final KeyedExtensionCollector<VirtualFileSystem, String> myCollector =
    new KeyedExtensionCollector<VirtualFileSystem, String>("com.intellij.virtualFileSystem") {
      @NotNull
      @Override
      protected String keyToString(@NotNull String key) {
        return key;
      }
    };

  private final VirtualFileSystem[] myPhysicalFileSystems;
  private final EventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster = EventDispatcher.create(VirtualFileListener.class);
  private final List<VirtualFileManagerListener> myVirtualFileManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private int myRefreshCount;

  public VirtualFileManagerImpl(@NotNull VirtualFileSystem[] fileSystems, @NotNull MessageBus bus) {
    myPhysicalFileSystems = fileSystems;

    for (VirtualFileSystem fileSystem : fileSystems) {
      myCollector.addExplicitExtension(fileSystem.getProtocol(), fileSystem);
      if (!(fileSystem instanceof CachingVirtualFileSystem)) {
        fileSystem.addVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
      }
    }

    if (LOG.isDebugEnabled() && !ApplicationInfoImpl.isInStressTest()) {
      addVirtualFileListener(new LoggingListener());
    }

    bus.connect().subscribe(VFS_CHANGES, new BulkVirtualFileListenerAdapter(myVirtualFileListenerMulticaster.getMulticaster()));
  }

  @Override
  public long getStructureModificationCount() {
    return 0;
  }

  @Override
  @Nullable
  public VirtualFileSystem getFileSystem(@Nullable String protocol) {
    if (protocol == null) return null;
    List<VirtualFileSystem> systems = myCollector.forKey(protocol);
    int size = systems.size();
    if (size == 0) return null;
    if (size > 1) {
      LOG.error(protocol + ": " + systems);
    }
    return systems.get(0);
  }

  @Override
  public long syncRefresh() {
    return doRefresh(false, null);
  }

  @Override
  public long asyncRefresh(@Nullable Runnable postAction) {
    return doRefresh(true, postAction);
  }

  protected long doRefresh(boolean asynchronous, @Nullable Runnable postAction) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    for (VirtualFileSystem fileSystem : myPhysicalFileSystems) {
      if (!(fileSystem instanceof CachingVirtualFileSystem)) {
        fileSystem.refresh(asynchronous);
      }
    }

    return 0;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    for (VirtualFileSystem fileSystem : myPhysicalFileSystems) {
      if (fileSystem instanceof CachingVirtualFileSystem) {
        ((CachingVirtualFileSystem)fileSystem).refreshWithoutFileWatcher(asynchronous);
      }
      else {
        fileSystem.refresh(asynchronous);
      }
    }
  }

  @Override
  public VirtualFile findFileByUrl(@NotNull String url) {
    VirtualFileSystem fileSystem = getFileSystemForUrl(url);
    if (fileSystem == null) return null;
    return fileSystem.findFileByPath(extractPath(url));
  }

  @Override
  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    VirtualFileSystem fileSystem = getFileSystemForUrl(url);
    if (fileSystem == null) return null;
    return fileSystem.refreshAndFindFileByPath(extractPath(url));
  }

  @Nullable
  private VirtualFileSystem getFileSystemForUrl(String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    return getFileSystem(protocol);
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.addListener(listener);
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable parentDisposable) {
    myVirtualFileListenerMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.removeListener(listener);
  }

  @Override
  public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.add(listener);
  }

  @Override
  public void addVirtualFileManagerListener(@NotNull final VirtualFileManagerListener listener, @NotNull Disposable parentDisposable) {
    addVirtualFileManagerListener(listener);
    Disposer.register(parentDisposable, () -> removeVirtualFileManagerListener(listener));
  }

  @Override
  public void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.remove(listener);
  }

  @Override
  public void notifyPropertyChanged(@NotNull final VirtualFile virtualFile, @NotNull final String property, final Object oldValue, final Object newValue) {
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (virtualFile.isValid() && !application.isDisposed()) {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              List<VFilePropertyChangeEvent> events = Collections
                .singletonList(new VFilePropertyChangeEvent(this, virtualFile, property, oldValue, newValue, false));
              BulkFileListener listener = application.getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
              listener.before(events);
              listener.after(events);
            }
          });
        }
      }
    };
    application.invokeLater(runnable, ModalityState.NON_MODAL);
  }

  @Override
  public void fireBeforeRefreshStart(boolean asynchronous) {
    if (myRefreshCount++ == 0) {
      for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
        try {
          listener.beforeRefreshStart(asynchronous);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public void fireAfterRefreshFinish(boolean asynchronous) {
    if (--myRefreshCount == 0) {
      for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
        try {
          listener.afterRefreshFinish(asynchronous);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public long getModificationCount() {
    return 0;
  }

  private static class LoggingListener implements VirtualFileListener {
    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      LOG.debug("propertyChanged: file = " + event.getFile() + ", propertyName = " + event.getPropertyName() +
                ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      LOG.debug("contentsChanged: file = " + event.getFile() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      LOG.debug("fileCreated: file = " + event.getFile() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      LOG.debug("fileDeleted: file = " + event.getFile() + ", parent = " + event.getParent() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      LOG.debug("fileMoved: file = " + event.getFile() + ", oldParent = " + event.getOldParent() +
                ", newParent = " + event.getNewParent() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
      LOG.debug("fileCopied: file = " + event.getFile() + "originalFile = " + event.getOriginalFile() +
                ", requestor = " + event.getRequestor());
    }

    @Override
    public void beforeContentsChange(@NotNull VirtualFileEvent event) {
      LOG.debug("beforeContentsChange: file = " + event.getFile() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
      LOG.debug("beforePropertyChange: file = " + event.getFile() + ", propertyName = " + event.getPropertyName() +
                ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
      LOG.debug("beforeFileDeletion: file = " + event.getFile() + ", requestor = " + event.getRequestor());
      LOG.assertTrue(event.getFile().isValid());
    }

    @Override
    public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
      LOG.debug("beforeFileMovement: file = " + event.getFile() + ", oldParent = " + event.getOldParent() +
                ", newParent = " + event.getNewParent() + ", requestor = " + event.getRequestor());
    }
  }
}