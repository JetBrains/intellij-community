// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.CachingVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.EventDispatcher;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class  VirtualFileManagerImpl extends VirtualFileManagerEx implements Disposable {
  protected static final Logger LOG = Logger.getInstance(VirtualFileManagerImpl.class);

  // do not use extension point name to avoid map lookup on each event publishing
  private static final ExtensionPointImpl<VirtualFileManagerListener>
    MANAGER_LISTENER_EP = ((ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea()).getExtensionPoint("com.intellij.virtualFileManagerListener");

  private static class VirtualFileSystemBean extends KeyedLazyInstanceEP<VirtualFileSystem> {
    @Attribute
    public boolean physical;
  }

  private final KeyedExtensionCollector<VirtualFileSystem, String> myCollector = new KeyedExtensionCollector<>("com.intellij.virtualFileSystem");
  private final VirtualFileSystem[] myPhysicalFileSystems;
  private final EventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster = EventDispatcher.create(VirtualFileListener.class);
  private final List<VirtualFileManagerListener> myVirtualFileManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<AsyncFileListener> myAsyncFileListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private int myRefreshCount;

  public VirtualFileManagerImpl(@NotNull List<? extends VirtualFileSystem> fileSystems) {
    this(fileSystems, ApplicationManager.getApplication().getMessageBus());
  }

  public VirtualFileManagerImpl(@NotNull List<? extends VirtualFileSystem> fileSystems, @NotNull MessageBus bus) {
    List<VirtualFileSystem> physicalFileSystems = new ArrayList<>(fileSystems);

    ExtensionPoint<KeyedLazyInstance<VirtualFileSystem>> point = myCollector.getPoint();
    if (point != null) {
      for (KeyedLazyInstance<VirtualFileSystem> bean : point.getExtensionList()) {
        if (((VirtualFileSystemBean)bean).physical) {
          physicalFileSystems.add(bean.getInstance());
        }
      }
    }

    myPhysicalFileSystems = physicalFileSystems.toArray(new VirtualFileSystem[0]);

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
  public void dispose() {
  }

  @Override
  public long getStructureModificationCount() {
    return 0;
  }

  @Override
  @Nullable
  public VirtualFileSystem getFileSystem(@Nullable String protocol) {
    if (protocol == null) {
      return null;
    }

    List<VirtualFileSystem> systems = myCollector.forKey(protocol);
    int size = systems.size();
    if (size == 0) {
      return null;
    }

    if (size > 1) {
      LOG.error(protocol + ": " + systems);
    }
    return systems.get(0);
  }

  public List<VirtualFileSystem> getPhysicalFileSystems() {
    return Arrays.asList(myPhysicalFileSystems);
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
  private VirtualFileSystem getFileSystemForUrl(@NotNull String url) {
    String protocol = extractProtocol(url);
    return protocol == null ? null : getFileSystem(protocol);
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
  public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable) {
    addVirtualFileManagerListener(listener);
    Disposer.register(parentDisposable, () -> removeVirtualFileManagerListener(listener));
  }

  @Override
  public void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.remove(listener);
  }

  @Override
  public void addAsyncFileListener(@NotNull AsyncFileListener listener, @NotNull Disposable parentDisposable) {
    myAsyncFileListeners.add(listener);
    Disposer.register(parentDisposable, () -> myAsyncFileListeners.remove(listener));
  }

  @ApiStatus.Internal
  public List<AsyncFileListener> getAsyncFileListeners() {
    return Collections.unmodifiableList(myAsyncFileListeners);
  }

  @Override
  public void notifyPropertyChanged(@NotNull VirtualFile virtualFile, @VirtualFile.PropName @NotNull String property, Object oldValue, Object newValue) {
    Application app = ApplicationManager.getApplication();
    app.invokeLater(() -> {
      if (virtualFile.isValid() && !app.isDisposed()) {
        app.runWriteAction(() -> {
          List<VFileEvent> events = Collections.singletonList(new VFilePropertyChangeEvent(this, virtualFile, property, oldValue, newValue, false));
          BulkFileListener listener = app.getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
          listener.before(events);
          listener.after(events);
        });
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public void fireBeforeRefreshStart(boolean asynchronous) {
    if (myRefreshCount++ != 0) {
      return;
    }

    for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
      try {
        listener.beforeRefreshStart(asynchronous);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    ExtensionProcessingHelper.forEachExtensionSafe(listener -> listener.beforeRefreshStart(asynchronous), MANAGER_LISTENER_EP);
  }

  @Override
  public void fireAfterRefreshFinish(boolean asynchronous) {
    if (--myRefreshCount != 0) {
      return;
    }

    for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
      try {
        listener.afterRefreshFinish(asynchronous);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    ExtensionProcessingHelper.forEachExtensionSafe(listener -> listener.afterRefreshFinish(asynchronous), MANAGER_LISTENER_EP);
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
      LOG.debug("fileCopied: file = " + event.getFile() + ", originalFile = " + event.getOriginalFile() +
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

  @Override
  public int storeName(@NotNull String name) {
    throw new AbstractMethodError();
  }

  @NotNull
  @Override
  public CharSequence getVFileName(int nameId) {
    throw new AbstractMethodError();
  }
}