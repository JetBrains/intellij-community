// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.CachingVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.EventDispatcher;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VirtualFileManagerImpl extends VirtualFileManager implements Disposable {
  protected static final Logger LOG = Logger.getInstance(VirtualFileManagerImpl.class);

  // do not use extension point name to avoid map lookup on each event publishing
  private static final ExtensionPointImpl<VirtualFileManagerListener> MANAGER_LISTENER_EP =
    ((ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea()).getExtensionPoint("com.intellij.virtualFileManagerListener");

  private final List<? extends VirtualFileSystem> myPreCreatedFileSystems;

  private static final class VirtualFileSystemBean extends KeyedLazyInstanceEP<VirtualFileSystem> {
    @Attribute
    public boolean physical;
  }

  private final KeyedExtensionCollector<VirtualFileSystem, String> myCollector = new KeyedExtensionCollector<>(VirtualFileSystem.EP_NAME);
  private final EventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster = EventDispatcher.create(VirtualFileListener.class);
  private final List<VirtualFileManagerListener> virtualFileManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<AsyncFileListener> asyncFileListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private int refreshCount;

  public VirtualFileManagerImpl(@NotNull List<? extends VirtualFileSystem> preCreatedFileSystems) {
    this(preCreatedFileSystems, ApplicationManager.getApplication().getMessageBus());
  }

  public VirtualFileManagerImpl(@NotNull List<? extends VirtualFileSystem> preCreatedFileSystems, @NotNull MessageBus bus) {
    myPreCreatedFileSystems = new ArrayList<>(preCreatedFileSystems);

    for (VirtualFileSystem fileSystem : preCreatedFileSystems) {
      myCollector.addExplicitExtension(fileSystem.getProtocol(), fileSystem);
      if (!(fileSystem instanceof CachingVirtualFileSystem)) {
        fileSystem.addVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
      }
    }

    addVirtualFileListener(new LoggingListener());

    bus.connect().subscribe(VFS_CHANGES, new BulkVirtualFileListenerAdapter(myVirtualFileListenerMulticaster.getMulticaster()));
  }

  public @NotNull List<VirtualFileSystem> getPhysicalFileSystems() {
    List<VirtualFileSystem> physicalFileSystems = new ArrayList<>(myPreCreatedFileSystems);

    ExtensionPoint<KeyedLazyInstance<VirtualFileSystem>> point = myCollector.getPoint();
    if (point != null) {
      for (KeyedLazyInstance<VirtualFileSystem> bean : point.getExtensionList()) {
        if (((VirtualFileSystemBean)bean).physical) {
          VirtualFileSystem fileSystem = bean.getInstance();
          physicalFileSystems.add(fileSystem);
        }
      }
    }

    return physicalFileSystems;
  }

  @Override
  public void dispose() { }

  @Override
  public long getStructureModificationCount() {
    return 0;
  }

  @Override
  public @Nullable VirtualFileSystem getFileSystem(@Nullable String protocol) {
    if (protocol == null) return null;

    List<? extends VirtualFileSystem> candidates = getFileSystemsForProtocol(protocol);
    int size = candidates.size();
    if (size == 0) {
      return null;
    }
    if (size > 1) {
      LOG.error(">1 file system registered for protocol [" + protocol + "]: " + candidates);
    }
    return candidates.get(0);
  }

  protected @NotNull List<? extends VirtualFileSystem> getFileSystemsForProtocol(@NotNull String protocol) {
    return myCollector.forKey(protocol);
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
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    }

    for (VirtualFileSystem fileSystem : getPhysicalFileSystems()) {
      if (!(fileSystem instanceof CachingVirtualFileSystem)) {
        fileSystem.refresh(asynchronous);
      }
    }

    return 0;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    }

    for (VirtualFileSystem fileSystem : getPhysicalFileSystems()) {
      if (fileSystem instanceof CachingVirtualFileSystem) {
        ((CachingVirtualFileSystem)fileSystem).refreshWithoutFileWatcher(asynchronous);
      }
      else {
        fileSystem.refresh(asynchronous);
      }
    }
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
  public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, () -> removeVirtualFileManagerListener(listener));
    virtualFileManagerListeners.add(listener);
  }

  @Override
  public void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
    virtualFileManagerListeners.remove(listener);
  }

  @Override
  public void addAsyncFileListener(@NotNull AsyncFileListener listener, @NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, () -> asyncFileListeners.remove(listener));
    asyncFileListeners.add(listener);
  }

  @ApiStatus.Internal
  public @NotNull @Unmodifiable List<AsyncFileListener> withAsyncFileListeners(@NotNull @Unmodifiable List<AsyncFileListener> listeners) {
    return ContainerUtil.concat(listeners, asyncFileListeners);
  }

  @Override
  public void notifyPropertyChanged(@NotNull VirtualFile virtualFile,
                                    @VirtualFile.PropName @NotNull String property,
                                    Object oldValue,
                                    Object newValue) {
    Application app = ApplicationManager.getApplication();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (virtualFile.isValid()) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          List<VFileEvent> events =
            Collections.singletonList(new VFilePropertyChangeEvent(this, virtualFile, property, oldValue, newValue));
          BulkFileListener listener = app.getMessageBus().syncPublisher(VFS_CHANGES);
          listener.before(events);
          listener.after(events);
        });
      }
    }, ModalityState.nonModal());
  }

  @ApiStatus.Internal
  public void fireBeforeRefreshStart(boolean asynchronous) {
    if (refreshCount++ == 0) {
      for (VirtualFileManagerListener listener : virtualFileManagerListeners) {
        try {
          listener.beforeRefreshStart(asynchronous);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      MANAGER_LISTENER_EP.processWithPluginDescriptor((listener, pluginDescriptor) -> {
        listener.beforeRefreshStart(asynchronous);
        return Unit.INSTANCE;
      });
    }
  }

  @ApiStatus.Internal
  public void fireAfterRefreshFinish(boolean asynchronous) {
    if (--refreshCount == 0) {
      for (VirtualFileManagerListener listener : virtualFileManagerListeners) {
        try {
          listener.afterRefreshFinish(asynchronous);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      MANAGER_LISTENER_EP.processWithPluginDescriptor((listener, pluginDescriptor) -> {
        listener.afterRefreshFinish(asynchronous);
        return Unit.INSTANCE;
      });
    }
  }

  @Override
  public long getModificationCount() {
    return 0;
  }

  private static class LoggingListener implements VirtualFileListener {
    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      if (shouldLog()) {
        LOG.debug("propertyChanged: file = " + event.getFile() + ", propertyName = " + event.getPropertyName() +
                  ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      if (shouldLog()) {
        LOG.debug("contentsChanged: file = " + event.getFile() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      if (shouldLog()) {
        LOG.debug("fileCreated: file = " + event.getFile() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      if (shouldLog()) {
        LOG.debug("fileDeleted: file = " + event.getFile() + ", parent = " + event.getParent() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      if (shouldLog()) {
        LOG.debug("fileMoved: file = " + event.getFile() + ", oldParent = " + event.getOldParent() +
                  ", newParent = " + event.getNewParent() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
      if (shouldLog()) {
        LOG.debug("fileCopied: file = " + event.getFile() + ", originalFile = " + event.getOriginalFile() +
                  ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void beforeContentsChange(@NotNull VirtualFileEvent event) {
      if (shouldLog()) {
        LOG.debug("beforeContentsChange: file = " + event.getFile() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
      if (shouldLog()) {
        LOG.debug("beforePropertyChange: file = " + event.getFile() + ", propertyName = " + event.getPropertyName() +
                  ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
      }
    }

    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
      if (shouldLog()) {
        LOG.debug("beforeFileDeletion: file = " + event.getFile() + ", requestor = " + event.getRequestor());
        LOG.assertTrue(event.getFile().isValid());
      }
    }

    @Override
    public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
      if (shouldLog()) {
        LOG.debug("beforeFileMovement: file = " + event.getFile() + ", oldParent = " + event.getOldParent() +
                  ", newParent = " + event.getNewParent() + ", requestor = " + event.getRequestor());
      }
    }

    private static boolean shouldLog() {
      return LOG.isDebugEnabled() && !ApplicationManagerEx.isInStressTest();
    }
  }

  @Override
  public int storeName(@NotNull String name) {
    throw new AbstractMethodError();
  }

  @Override
  public @NotNull CharSequence getVFileName(int nameId) {
    throw new AbstractMethodError();
  }

  @Override
  public VirtualFile findFileByUrl(@NotNull String url) {
    return findByUrl(url, false);
  }

  @Override
  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return findByUrl(url, true);
  }

  private @Nullable VirtualFile findByUrl(@NotNull String url, boolean refresh) {
    int protocolSepIndex = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    VirtualFileSystem fileSystem = protocolSepIndex < 0 ? null : getFileSystem(url.substring(0, protocolSepIndex));
    if (fileSystem == null) return null;
    String path = url.substring(protocolSepIndex + URLUtil.SCHEME_SEPARATOR.length());
    return refresh ? fileSystem.refreshAndFindFileByPath(path) : fileSystem.findFileByPath(path);
  }

  @Override
  public @Nullable VirtualFile findFileByNioPath(@NotNull Path path) {
    return findByNioPath(path, false);
  }

  @Override
  public @Nullable VirtualFile refreshAndFindFileByNioPath(@NotNull Path path) {
    return findByNioPath(path, true);
  }

  private @Nullable VirtualFile findByNioPath(@NotNull Path nioPath, boolean refresh) {
    if (!FileSystems.getDefault().equals(nioPath.getFileSystem())) return null;
    VirtualFileSystem fileSystem = getFileSystem(StandardFileSystems.FILE_PROTOCOL);
    if (fileSystem == null) return null;
    String path = nioPath.toString();
    return refresh ? fileSystem.refreshAndFindFileByPath(path) : fileSystem.findFileByPath(path);
  }
}
