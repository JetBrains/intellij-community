// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR;

public final class VfsImplUtil {
  private static final Logger LOG = Logger.getInstance(VfsImplUtil.class);

  private VfsImplUtil() {
    throw new AssertionError("Static utils class: not for instantiation");
  }

  /**
   * Resolves the given path against the given fileSystem.
   *
   * @return VirtualFile for the path, if resolved, null if not -- e.g. path is invalid or doesn't exist, or not belongs to
   * fileSystem given.
   *
   * @deprecated use NewVirtualFileSystem.findFileByPath() instead.
   */
  @Deprecated(forRemoval = true)
  public static @Nullable NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem fileSystem,
                                                        @NotNull String path) {
    return NewVirtualFileSystem.findFileByPath(fileSystem, path);
  }

  /** @deprecated use NewVirtualFileSystem.findFileByPathIfCached() instead.*/
  @Deprecated(forRemoval = true)
  public static @Nullable NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem fileSystem,
                                                                @NotNull String path){
    return NewVirtualFileSystem.findFileByPathIfCached(fileSystem, path);
  }

  public static @Nullable NewVirtualFile refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = NewVirtualFileSystem.extractRootAndPathSegments(vfs, path);
    if (rootAndPath == null) return null;

    NewVirtualFile file = rootAndPath.first;
    for (String pathElement : rootAndPath.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          String canonicalPath = file.getCanonicalPath();
          NewVirtualFile canonicalFile = canonicalPath != null ? refreshAndFindFileByPath(vfs, canonicalPath) : null;
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.refreshAndFindChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  /** An experimental refresh-and-find routine that doesn't require a write-lock (and hence EDT). */
  @ApiStatus.Experimental
  public static void refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs,
                                              @NotNull String path,
                                              @NotNull Consumer<? super @Nullable NewVirtualFile> consumer) {
    ProcessIOExecutorService.INSTANCE.execute(() -> {
      Pair<NewVirtualFile, Iterable<String>> rootAndPath = NewVirtualFileSystem.extractRootAndPathSegments(vfs, path);
      if (rootAndPath == null) {
        consumer.accept(null);
      }
      else {
        NewVirtualFile root = rootAndPath.first;
        Iterable<String> pathSegments = rootAndPath.second;
        refreshAndFindFileByPath(root, pathSegments.iterator(), consumer);
      }
    });
  }

  private static void refreshAndFindFileByPath(@Nullable NewVirtualFile root,
                                               @NotNull Iterator<String> pathSegments,
                                               @NotNull Consumer<? super @Nullable NewVirtualFile> consumer) {
    if (root == null || !pathSegments.hasNext()) {
      consumer.accept(root);
      return;
    }

    String pathSegment = pathSegments.next();
    if (pathSegment.isEmpty() || ".".equals(pathSegment)) {
      refreshAndFindFileByPath(root, pathSegments, consumer);
    }
    else if ("..".equals(pathSegment)) {
      if (root.is(VFileProperty.SYMLINK)) {//resolve the symlink then:
        String rootPathCanonicalized = root.getCanonicalPath();
        if (rootPathCanonicalized != null) {
          //TODO RC: shouldn't we use rootPathCanonicalized.getParent() here? Because we already cut 1st segment from pathSegments.
          refreshAndFindFileByPath(root.getFileSystem(), rootPathCanonicalized,
                                   canonicalFile -> refreshAndFindFileByPath(canonicalFile, pathSegments, consumer));
        }
        else {//symlink unresolved -- broken link?
          consumer.accept(null);
        }
      }
      else {
        refreshAndFindFileByPath(root.getParent(), pathSegments, consumer);
      }
    }
    else {
      NewVirtualFile child = root.findChild(pathSegment);
      if (child != null) {
        refreshAndFindFileByPath(child, pathSegments, consumer);
      }
      else {
        root.refresh(true, false,
                     () -> ProcessIOExecutorService.INSTANCE.execute(
                       () -> refreshAndFindFileByPath(root.findChild(pathSegment), pathSegments, consumer)
                     )
        );
      }
    }
  }

  public static void refresh(@NotNull NewVirtualFileSystem vfs, boolean asynchronous) {
    VirtualFile[] roots = ManagingFS.getInstance().getRoots(vfs);
    if (roots.length > 0) {
      RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
    }
  }

  /**
   * Guru method for force synchronous file refresh.
   * <p>
   * Refreshing files via {@link #refresh(NewVirtualFileSystem, boolean)} doesn't work well if the file was changed
   * twice in short time and content length wasn't changed (for example, file modification timestamp for HFS+ works per seconds).
   * <p>
   * If you're sure that a file is changed twice in a second, and you have to get the latest file's state - use this method.
   * <p>
   * Likely you need this method if you have the following code:
   * <code><pre>
   * FileDocumentManager.getInstance().saveDocument(document);
   * runExternalToolToChangeFile(virtualFile.getPath()) // changes file externally in milliseconds, probably without changing file's length
   * VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile); // might be replaced with forceSyncRefresh(virtualFile);
   * </pre></code>
   */
  public static void forceSyncRefresh(@NotNull VirtualFile file) {
    var event = new VFileContentChangeEvent(REFRESH_REQUESTOR, file, file.getModificationStamp(),
                                            VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH);
    RefreshQueue.getInstance().processEvents(false, List.of(event));
  }

  private static final AtomicBoolean ourSubscribed = new AtomicBoolean(false);
  private static final Object ourLock = new Object();
  private static final Map<String, Pair<ArchiveFileSystem, ArchiveHandler>> ourHandlerCache = CollectionFactory.createFilePathMap();
  // guarded by ourLock
  private static final Map<String, Set<String>> ourDominatorsMap = CollectionFactory.createFilePathMap(); // guarded by ourLock; values too

  public static @NotNull <T extends ArchiveHandler> T getHandler(@NotNull ArchiveFileSystem vfs,
                                                                 @NotNull VirtualFile entryFile,
                                                                 @NotNull Function<? super String, ? extends T> producer) {
    String localPath = ArchiveFileSystem.getLocalPath(vfs, VfsUtilCore.getRootFile(entryFile).getPath());
    checkSubscription();

    T handler;

    synchronized (ourLock) {
      Pair<ArchiveFileSystem, ArchiveHandler> record = ourHandlerCache.get(localPath);

      if (record == null) {
        handler = producer.fun(localPath);
        record = Pair.create(vfs, handler);
        ourHandlerCache.put(localPath, record);

        forEachDirectoryComponent(localPath, containingDirectoryPath -> {
          Set<String> handlers = ourDominatorsMap.computeIfAbsent(containingDirectoryPath, __ -> new HashSet<>());
          handlers.add(localPath);
        });
      }

      //noinspection unchecked
      handler = (T)record.second;
    }

    return handler;
  }

  private static void forEachDirectoryComponent(@NotNull String rootPath, @NotNull Consumer<? super String> consumer) {
    int index = rootPath.lastIndexOf('/');
    while (index > 0) {
      String containingDirectoryPath = rootPath.substring(0, index);
      consumer.accept(containingDirectoryPath);
      index = rootPath.lastIndexOf('/', index - 1);
    }
  }

  private static void checkSubscription() {
    if (ourSubscribed.getAndSet(true)) return;

    Application app = ApplicationManager.getApplication();
    if (app.isDisposed()) return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)

    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        InvalidationState state = null;

        synchronized (ourLock) {
          for (var event : events) {
            if (!(event.getFileSystem() instanceof LocalFileSystem)) continue;
            if (!(event instanceof VFileContentChangeEvent contentChangeEvent)) continue;

            String path = contentChangeEvent.getPath();
            VirtualFile file = contentChangeEvent.getFile();
            if (!file.isDirectory()) {
              state = invalidate(state, path);
            }
            else {
              Collection<String> affectedPaths = ourDominatorsMap.get(path);
              if (affectedPaths != null) {
                affectedPaths = new ArrayList<>(affectedPaths);  // defensive copying; original may be updated on invalidation
                for (String affectedPath : affectedPaths) {
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
  private static @Nullable InvalidationState invalidate(@Nullable InvalidationState state, @NotNull String path) {
    Pair<ArchiveFileSystem, ArchiveHandler> fsAndHandler = ourHandlerCache.remove(path);
    if (fsAndHandler != null) {
      fsAndHandler.second.clearCaches();

      forEachDirectoryComponent(path, containingDirectoryPath -> {
        Set<String> handlers = ourDominatorsMap.get(containingDirectoryPath);
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

    private void registerPathToRefresh(@NotNull String path, @NotNull ArchiveFileSystem vfs) {
      if (myRootsToRefresh == null) myRootsToRefresh = new HashSet<>();
      myRootsToRefresh.add(Pair.create(path, vfs));
    }

    private void scheduleRefresh() {
      if (myRootsToRefresh != null) {
        List<@NotNull NewVirtualFile> rootsToRefresh = ContainerUtil.mapNotNull(
          myRootsToRefresh,
          pathAndFs -> ManagingFS.getInstance()
            .findRoot(ArchiveFileSystem.composeRootPath(pathAndFs.second, pathAndFs.first), pathAndFs.second));
        for (@NotNull NewVirtualFile root : rootsToRefresh) {
          root.markDirtyRecursively();
        }
        boolean synchronous = ApplicationManager.getApplication().isUnitTestMode();
        RefreshQueue.getInstance().refresh(!synchronous, true, null, rootsToRefresh);
      }
    }
  }

  @TestOnly
  public static void releaseHandler(@NotNull String localPath) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) throw new IllegalStateException();
    synchronized (ourLock) {
      InvalidationState state = invalidate(null, localPath);
      if (state == null) throw new IllegalArgumentException(localPath + " not in " + ourHandlerCache.keySet());
    }
  }

  /**
   * <p>Checks whether the {@code event} (in {@link LocalFileSystem}) affects some archives and if so,
   * generates appropriate additional {@link JarFileSystem}-events and corresponding after-event-actions.</p>
   * <p>For example, {@link VFileDeleteEvent}/{@link VFileMoveEvent}/{@link VFilePropertyChangeEvent VFilePropertyChangeEvent(PROP_NAME)}('file://tmp/x.jar')
   * should generate {@link VFileDeleteEvent}('jar:///tmp/x.jar!/').</p>
   * And vice versa, when refresh found change inside jar archive, generate {@link LocalFileSystem}-level events
   * for the corresponding .jar file change.
   * <p>For example, {@link VFileDeleteEvent}/{@link VFileMoveEvent}/{@link VFilePropertyChangeEvent VFilePropertyChangeEvent(PROP_NAME)}('jar:///x.jar!/')
   * should generate {@link VFileDeleteEvent}('file://x.jar').</p>
   * (The latter might happen when someone explicitly called {@code fileInsideJar.refresh()} without refreshing jar file in local file system).
   */
  public static @NotNull List<VFileEvent> getJarInvalidationEvents(@NotNull VFileEvent event,
                                                                   @NotNull List<? super Runnable> outApplyActions) {
    if (!(event instanceof VFileDeleteEvent ||
          event instanceof VFileMoveEvent ||
          event instanceof VFilePropertyChangeEvent propertyChangeEvent &&
          VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName()))) {
      return Collections.emptyList();
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

    VirtualFile file = event.getFile();
    VirtualFileSystem entryFileSystem = file.getFileSystem();
    VirtualFile local = null;
    if (entryFileSystem instanceof ArchiveFileSystem) {
      local = ((ArchiveFileSystem)entryFileSystem).getLocalByEntry(file);
      path = local == null ? ArchiveFileSystem.getLocalPath((ArchiveFileSystem)entryFileSystem, path) : local.getPath();
    }
    String[] jarPaths;
    synchronized (ourLock) {
      Set<String> handlers = ourDominatorsMap.get(path);
      if (handlers == null) {
        jarPaths = new String[]{path};
      }
      else {
        jarPaths = ArrayUtil.toStringArray(handlers);
      }
    }
    List<VFileEvent> events = new ArrayList<>(jarPaths.length);
    for (String jarPath : jarPaths) {
      Pair<ArchiveFileSystem, ArchiveHandler> handlerPair = ourHandlerCache.get(jarPath);
      if (handlerPair == null) {
        continue;
      }
      if (entryFileSystem instanceof LocalFileSystem) {
        ArchiveFileSystem fileSystem = handlerPair.first;
        NewVirtualFile root = ManagingFS.getInstance().findRoot(ArchiveFileSystem.composeRootPath(fileSystem, jarPath), fileSystem);
        if (root != null) {
          VFileDeleteEvent jarDeleteEvent = new VFileDeleteEvent(event.getRequestor(), root);
          Runnable runnable = () -> {
            Pair<ArchiveFileSystem, ArchiveHandler> pair = ourHandlerCache.remove(jarPath);
            if (pair != null) {
              pair.second.clearCaches();
              synchronized (ourLock) {
                forEachDirectoryComponent(jarPath, containingDirectoryPath -> {
                  Set<String> handlers = ourDominatorsMap.get(containingDirectoryPath);
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
        VFileEvent localJarDeleteEvent = file.getParent() == null ?
                                         new VFileDeleteEvent(event.getRequestor(), local) :
                                         new VFileContentChangeEvent(event.getRequestor(), local, local.getModificationStamp(),
                                                                     local.getModificationStamp());
        events.add(localJarDeleteEvent);
      }
    }
    return events;
  }
}
