// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.VFileProperty.SYMLINK;
import static com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR;

public final class VfsImplUtil {
  private VfsImplUtil() {
    throw new AssertionError("Static utils class: not for instantiation");
  }

  /// Resolves the given path against the given fileSystem.
  ///
  /// @return VirtualFile for the path, if resolved, null if not -- e.g. path is invalid or doesn't exist, or not belongs to
  /// fileSystem given.
  /// @deprecated use NewVirtualFileSystem.findFileByPath() instead.
  @Deprecated(forRemoval = true)
  public static @Nullable NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem fileSystem, @NotNull String path) {
    return NewVirtualFileSystem.findFileByPath(fileSystem, path);
  }

  /// @deprecated use NewVirtualFileSystem.findFileByPathIfCached() instead.
  @Deprecated(forRemoval = true)
  public static @Nullable NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem fileSystem, @NotNull String path) {
    return NewVirtualFileSystem.findFileByPathIfCached(fileSystem, path);
  }

  public static @Nullable NewVirtualFile refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    var navigator = new FileNavigator<NewVirtualFile>() {
      @Override
      public @Nullable NewVirtualFile parentOf(@NotNull NewVirtualFile file) {
        if (!file.is(SYMLINK)) {
          return file.getParent();
        }
        var canonicalPath = file.getCanonicalPath();
        return canonicalPath != null ? refreshAndFindFileByPath(vfs, canonicalPath) : null;
      }

      @Override
      public @Nullable NewVirtualFile childOf(@NotNull NewVirtualFile parent, @NotNull String childName) {
        return parent.refreshAndFindChild(childName);
      }
    };
    var result = FileNavigator.navigate(vfs, path, navigator);
    return result.resolvedFileOr(null);
  }

  /// An experimental refresh-and-find routine that doesn't require a write-lock (and hence EDT).
  @ApiStatus.Experimental
  public static void refreshAndFindFileByPath(
    @NotNull NewVirtualFileSystem vfs,
    @NotNull String path,
    @NotNull Consumer<? super @Nullable NewVirtualFile> consumer
  ) {
    ProcessIOExecutorService.INSTANCE.execute(() -> {
      var rootAndPath = NewVirtualFileSystem.extractRootAndPathSegments(vfs, path);
      if (rootAndPath == null) {
        consumer.accept(null);
      }
      else {
        var root = rootAndPath.first;
        var pathSegments = rootAndPath.second;
        refreshAndFindFileByPath(root, pathSegments.iterator(), FileNavigator.POSIX_LIGHT, consumer);
      }
    });
  }

  private static void refreshAndFindFileByPath(
    @Nullable NewVirtualFile root,
    Iterator<String> pathSegments,
    FileNavigator<NewVirtualFile> navigator,
    Consumer<? super @Nullable NewVirtualFile> consumer
  ) {
    if (root == null || !pathSegments.hasNext()) {
      consumer.accept(root);
      return;
    }

    var pathSegment = pathSegments.next();
    if (pathSegment.isEmpty() || ".".equals(pathSegment)) {
      refreshAndFindFileByPath(root, pathSegments, navigator, consumer);
      return;
    }

    if ("..".equals(pathSegment)) {
      var parent = navigator.parentOf(root);
      if (parent == null) {
        consumer.accept(null);
      }
      else {
        var rootPathCanonicalized = parent.getPath();
        refreshAndFindFileByPath(
          root.getFileSystem(), rootPathCanonicalized,
          canonicalFile -> refreshAndFindFileByPath(canonicalFile, pathSegments, navigator, consumer));
      }
    }
    else {
      var child = navigator.childOf(root, pathSegment);
      if (child != null) {
        refreshAndFindFileByPath(child, pathSegments, navigator, consumer);
      }
      else {
        root.refresh(
          /*async: */ true,
          /*recursive: */ false,
          () -> ProcessIOExecutorService.INSTANCE.execute(
            () -> refreshAndFindFileByPath(navigator.childOf(root, pathSegment), pathSegments, navigator, consumer)
          )
        );
      }
    }
  }

  public static void refresh(@NotNull NewVirtualFileSystem vfs, boolean asynchronous) {
    var roots = ManagingFS.getInstance().getRoots(vfs);
    if (roots.length > 0) {
      RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
    }
  }

  /// Guru method for force synchronous file refresh.
  ///
  /// Refreshing files via [#refresh(NewVirtualFileSystem, boolean)] doesn't work well if the file was changed
  /// twice in short time and content length wasn't changed (for example, file modification timestamp for HFS+ works per seconds).
  ///
  /// If you're sure that a file is changed twice in a second, and you have to get the latest file's state - use this method.
  ///
  /// Likely you need this method if you have the following code:
  /// ``
  ///
  /// ```
  /// FileDocumentManager.getInstance().saveDocument(document);
  /// runExternalToolToChangeFile(virtualFile.getPath()) // changes file externally in milliseconds, probably without changing file's length
  /// VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile); // might be replaced with forceSyncRefresh(virtualFile);
  /// ```
  public static void forceSyncRefresh(@NotNull VirtualFile file) {
    var event = new VFileContentChangeEvent(REFRESH_REQUESTOR, file, file.getModificationStamp(), VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH);
    RefreshQueue.getInstance().processEvents(false, List.of(event));
  }

  private static final AtomicBoolean ourSubscribed = new AtomicBoolean(false);
  private static final Object ourLock = new Object();
  private static final Map<String, Pair<ArchiveFileSystem, ArchiveHandler>> ourHandlerCache = CollectionFactory.createFilePathMap();
  // guarded by ourLock
  private static final Map<String, Set<String>> ourDominatorsMap = CollectionFactory.createFilePathMap(); // guarded by ourLock; values too

  public static @NotNull <T extends ArchiveHandler> T getHandler(
    @NotNull ArchiveFileSystem vfs,
    @NotNull VirtualFile entryFile,
    @NotNull Function<? super String, ? extends T> producer
  ) {
    var localPath = ArchiveFileSystem.getLocalPath(vfs, VfsUtilCore.getRootFile(entryFile).getPath());
    checkSubscription();

    T handler;

    synchronized (ourLock) {
      var record = ourHandlerCache.get(localPath);

      if (record == null) {
        handler = producer.fun(localPath);
        record = Pair.create(vfs, handler);
        ourHandlerCache.put(localPath, record);

        forEachDirectoryComponent(localPath, containingDirectoryPath -> {
          var handlers = ourDominatorsMap.computeIfAbsent(containingDirectoryPath, _ -> new HashSet<>());
          handlers.add(localPath);
        });
      }

      //noinspection unchecked
      handler = (T)record.second;
    }

    return handler;
  }

  private static void forEachDirectoryComponent(@NotNull String rootPath, @NotNull Consumer<? super String> consumer) {
    var index = rootPath.lastIndexOf('/');
    while (index > 0) {
      var containingDirectoryPath = rootPath.substring(0, index);
      consumer.accept(containingDirectoryPath);
      index = rootPath.lastIndexOf('/', index - 1);
    }
  }

  private static void checkSubscription() {
    if (ourSubscribed.getAndSet(true)) return;

    var app = ApplicationManager.getApplication();
    if (app.isDisposed()) return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)

    var connection = app.getMessageBus().connect(app);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        InvalidationState state = null;

        synchronized (ourLock) {
          for (var event : events) {
            if (!(event.getFileSystem() instanceof LocalFileSystem)) continue;
            if (!(event instanceof VFileContentChangeEvent contentChangeEvent)) continue;

            var path = contentChangeEvent.getPath();
            var file = contentChangeEvent.getFile();
            if (!file.isDirectory()) {
              state = invalidate(state, path);
            }
            else {
              Collection<String> affectedPaths = ourDominatorsMap.get(path);
              if (affectedPaths != null) {
                affectedPaths = new ArrayList<>(affectedPaths);  // defensive copying; original may be updated on invalidation
                for (var affectedPath : affectedPaths) {
                  state = invalidate(state, affectedPath);
                }
              }
            }
          }
        }

        if (state != null) state.scheduleRefresh();
      }
    });
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        synchronized (ourLock) {
          // avoid leaking ArchiveFileSystem registered by plugin, e.g. TgzFileSystem from Kubernetes plugin
          ourHandlerCache.clear();
        }
      }
    });
  }

  // must be called under ourLock
  private static @Nullable InvalidationState invalidate(@Nullable InvalidationState state, String path) {
    var fsAndHandler = ourHandlerCache.remove(path);
    if (fsAndHandler != null) {
      fsAndHandler.second.clearCaches();

      forEachDirectoryComponent(path, containingDirectoryPath -> {
        var handlers = ourDominatorsMap.get(containingDirectoryPath);
        if (handlers != null && handlers.remove(path) && handlers.isEmpty()) {
          ourDominatorsMap.remove(containingDirectoryPath);
        }
      });

      if (state == null) state = new InvalidationState();
      state.registerPathToRefresh(path, fsAndHandler.first);
    }

    return state;
  }

  private static final class InvalidationState {
    private Set<Pair<String, ArchiveFileSystem>> myRootsToRefresh;

    private void registerPathToRefresh(String path, ArchiveFileSystem vfs) {
      if (myRootsToRefresh == null) myRootsToRefresh = new HashSet<>();
      myRootsToRefresh.add(Pair.create(path, vfs));
    }

    private void scheduleRefresh() {
      if (myRootsToRefresh != null) {
        var rootsToRefresh = ContainerUtil.mapNotNull(
          myRootsToRefresh,
          pathAndFs -> ManagingFS.getInstance()
            .findRoot(ArchiveFileSystem.composeRootPath(pathAndFs.second, pathAndFs.first), pathAndFs.second));
        for (@NotNull NewVirtualFile root : rootsToRefresh) {
          root.markDirtyRecursively();
        }
        var synchronous = ApplicationManager.getApplication().isUnitTestMode();
        RefreshQueue.getInstance().refresh(!synchronous, true, null, rootsToRefresh);
      }
    }
  }

  @TestOnly
  public static void releaseHandler(@NotNull String localPath) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) throw new IllegalStateException();
    synchronized (ourLock) {
      var state = invalidate(null, localPath);
      if (state == null) throw new IllegalArgumentException(localPath + " not in " + ourHandlerCache.keySet());
    }
  }

  /// Checks whether the `event` (in [LocalFileSystem]) affects some archives and if so,
  /// generates appropriate additional [com.intellij.openapi.vfs.JarFileSystem]-events and corresponding after-event-actions.
  ///
  /// For example, [VFileDeleteEvent]/[VFileMoveEvent]/[`VFilePropertyChangeEvent(PROP\_NAME)`][VFilePropertyChangeEvent]('file://tmp/x.jar')
  /// should generate [VFileDeleteEvent]('jar:///tmp/x.jar!/').
  ///
  /// And vice versa, when refresh found change inside jar archive, generate [LocalFileSystem]-level events
  /// for the corresponding .jar file change.
  ///
  /// For example, [VFileDeleteEvent]/[VFileMoveEvent]/[`VFilePropertyChangeEvent(PROP\_NAME)`][VFilePropertyChangeEvent]('jar:///x.jar!/')
  /// should generate [VFileDeleteEvent]('file://x.jar').
  ///
  /// (The latter might happen when someone explicitly called `fileInsideJar.refresh()` without refreshing jar file in local file system).
  public static @NotNull List<VFileEvent> getJarInvalidationEvents(@NotNull VFileEvent event, @NotNull List<? super Runnable> outApplyActions) {
    if (!(
      event instanceof VFileDeleteEvent ||
      event instanceof VFileMoveEvent ||
      event instanceof VFilePropertyChangeEvent propertyChangeEvent && VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())
    )) {
      return List.of();
    }

    String path;
    if (event instanceof VFilePropertyChangeEvent propertyChangeEvent) {
      path = propertyChangeEvent.getOldPath();
    }
    else if (event instanceof VFileMoveEvent moveEvent) {
      path = moveEvent.getOldPath();
    }
    else {
      path = event.getPath();
    }

    var file = event.getFile();
    var entryFileSystem = file.getFileSystem();
    var local = (VirtualFile)null;
    if (entryFileSystem instanceof ArchiveFileSystem) {
      local = ((ArchiveFileSystem)entryFileSystem).getLocalByEntry(file);
      path = local == null ? ArchiveFileSystem.getLocalPath((ArchiveFileSystem)entryFileSystem, path) : local.getPath();
    }
    String[] jarPaths;
    synchronized (ourLock) {
      var handlers = ourDominatorsMap.get(path);
      if (handlers == null) {
        jarPaths = new String[]{path};
      }
      else {
        jarPaths = ArrayUtil.toStringArray(handlers);
      }
    }
    var events = new ArrayList<VFileEvent>(jarPaths.length);
    for (var jarPath : jarPaths) {
      var handlerPair = ourHandlerCache.get(jarPath);
      if (handlerPair == null) {
        continue;
      }
      if (entryFileSystem instanceof LocalFileSystem) {
        var fileSystem = handlerPair.first;
        var root = ManagingFS.getInstance().findRoot(ArchiveFileSystem.composeRootPath(fileSystem, jarPath), fileSystem);
        if (root != null) {
          var jarDeleteEvent = new VFileDeleteEvent(event.getRequestor(), root);
          Runnable runnable = () -> {
            var pair = ourHandlerCache.remove(jarPath);
            if (pair != null) {
              pair.second.clearCaches();
              synchronized (ourLock) {
                forEachDirectoryComponent(jarPath, containingDirectoryPath -> {
                  var handlers = ourDominatorsMap.get(containingDirectoryPath);
                  if (handlers != null && handlers.remove(jarPath) && handlers.isEmpty()) {
                    ourDominatorsMap.remove(containingDirectoryPath);
                  }
                });
              }
            }
          };
          events.add(jarDeleteEvent);
          outApplyActions.add(runnable);
        }
      }
      else if (local != null) {
        // for "delete jar://x.jar!/" generate "delete file://x.jar", but
        // for "delete jar://x.jar!/web.xml" generate "changed file://x.jar"
        var localJarDeleteEvent =
          file.getParent() == null ?
          new VFileDeleteEvent(event.getRequestor(), local) :
          new VFileContentChangeEvent(event.getRequestor(), local, local.getModificationStamp(), local.getModificationStamp());
        events.add(localJarDeleteEvent);
      }
    }
    return events;
  }
}
