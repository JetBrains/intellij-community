// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService;
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.serviceContainer.ComponentManagerImplKt.handleComponentError;
import static com.intellij.util.indexing.FileBasedIndexDataInitialization.FileBasedIndexDataInitializationResult;

final class FileBasedIndexDataInitialization extends IndexDataInitializer<FileBasedIndexDataInitializationResult> {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexDataInitialization.class);

  private boolean myCurrentVersionCorrupted;

  private final @NotNull FileBasedIndexImpl myFileBasedIndex;
  private final @NotNull RegisteredIndexes myRegisteredIndexes;
  private final @NotNull IntSet myStaleIds = IntSets.synchronize(new IntOpenHashSet());
  private volatile OrphanDirtyFilesQueue myOrphanDirtyFilesQueue;
  private final @NotNull IndexVersionRegistrationSink myRegistrationResultSink = new IndexVersionRegistrationSink();
  private final @NotNull IndexConfiguration myState = new IndexConfiguration();

  FileBasedIndexDataInitialization(@NotNull FileBasedIndexImpl index, @NotNull RegisteredIndexes registeredIndexes) {
    super("file based index");
    myFileBasedIndex = index;
    myRegisteredIndexes = registeredIndexes;
  }

  private @NotNull Collection<ThrowableRunnable<?>> initAssociatedDataForExtensions(@NotNull OrphanDirtyFilesQueue orphanDirtyFilesQueue) {
    Activity activity = StartUpMeasurer.startActivity("file index extensions iteration");
    Iterator<FileBasedIndexExtension<?, ?>> extensions = FileBasedIndexExtension.EXTENSION_POINT_NAME.getIterable().iterator();
    List<ThrowableRunnable<?>> tasks = new ArrayList<>(FileBasedIndexExtension.EXTENSION_POINT_NAME.getPoint().size());

    IntSet allDirtyFiles = new IntOpenHashSet(orphanDirtyFilesQueue.getFileIds());
    if (StaleIndexesChecker.shouldCheckStaleIndexesOnStartup()) {
      readAllProjectDirtyFilesQueues(allDirtyFiles);
    }
    // todo: init contentless indices first ?
    while (extensions.hasNext()) {
      FileBasedIndexExtension<?, ?> extension = extensions.next();
      RebuildStatus.registerIndex(extension.getName());

      tasks.add(() -> {
        if (IOUtil.isSharedCachesEnabled()) {
          IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
        }
        try {
          myStaleIds.addAll(FileBasedIndexImpl.registerIndexer(extension,
                                                               myState,
                                                               myRegistrationResultSink,
                                                               allDirtyFiles));

          // FileBasedIndexImpl.registerIndexer may throw, then the line below will not be executed
          myRegisteredIndexes.registerIndexExtension(extension);
        }
        catch (IOException | AlreadyDisposedException | ProcessCanceledException e) {
          LOG.warnWithDebug("Could not register indexing extension: " + extension + ". reason: " + e, e);
          ID.unloadId(extension.getName());
          throw e;
        }
        catch (Throwable t) {
          LOG.warnWithDebug("Could not register indexing extension: " + extension + ". reason: " + t, t);
          ID.unloadId(extension.getName());
          handleComponentError(t, extension.getClass().getName(), null);
        }
        finally {
          IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
        }
      });
    }

    myRegisteredIndexes.extensionsDataWasLoaded();
    activity.end();

    return tasks;
  }

  public static void readAllProjectDirtyFilesQueues(@NotNull IntSet dirtyFiles) {
    File[] projectQueueFiles = PersistentDirtyFilesQueue.getQueuesDir().toFile().listFiles();
    if (projectQueueFiles != null) {
      for (File file : projectQueueFiles) {
        dirtyFiles.addAll(PersistentDirtyFilesQueue.readProjectDirtyFilesQueue(file.toPath(), ManagingFS.getInstance().getCreationTimestamp()).getFileIds());
      }
    }
  }

  @Override
  protected @NotNull Collection<ThrowableRunnable<?>> prepareTasks() {
    // PersistentFS lifecycle should contain FileBasedIndex lifecycle, so,
    // 1) we call for it's instance before index creation to make sure it's initialized
    // 2) we dispose FileBasedIndex before PersistentFS disposing
    PersistentFSImpl fs = (PersistentFSImpl)ManagingFS.getInstance();
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    Disposable disposable = new ShutdownTaskAsDisposable();
    ApplicationManager.getApplication().addApplicationListener(new MyApplicationListener(fileBasedIndex), disposable);
    Disposer.register(fs, disposable);
    //Generally, Index will be shutdown by Disposer -- but to be sure, we'll register a shutdown task also:
    myFileBasedIndex.setUpShutDownTask();

    myCurrentVersionCorrupted = CorruptionMarker.requireInvalidation();
    for (FileBasedIndexInfrastructureExtension extension : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
      FileBasedIndexInfrastructureExtension.InitializationResult result = extension.initialize(IndexStorageLayoutLocator.getCustomLayoutId());
      myCurrentVersionCorrupted = myCurrentVersionCorrupted ||
                                  result == FileBasedIndexInfrastructureExtension.InitializationResult.INDEX_REBUILD_REQUIRED;
    }

    if (myCurrentVersionCorrupted) {
      CorruptionMarker.dropIndexes();
      ApplicationManager.getApplication().getService(AppIndexingDependenciesService.class).invalidateAllStamps("Indexes corrupted");
    }

    myOrphanDirtyFilesQueue = PersistentDirtyFilesQueue.readOrphanDirtyFilesQueue(PersistentDirtyFilesQueue.getQueueFile(), ManagingFS.getInstance().getCreationTimestamp());
    Collection<ThrowableRunnable<?>> tasks = initAssociatedDataForExtensions(myOrphanDirtyFilesQueue);

    PersistentIndicesConfiguration.loadConfiguration();

    return tasks;
  }

  // a static class is required to make sure a new instance is created and `this` is not leaked
  private static class ShutdownTaskAsDisposable implements Disposable {
    @Override
    public void dispose() {
      new FileBasedIndexImpl.ShutDownIndexesTask(/*byShutDownHook: */ false).run();
    }
  }

  @Override
  protected @NotNull FileBasedIndexDataInitializationResult finish() {
    try {
      myState.finalizeFileTypeMappingForIndices();

      showChangedIndexesNotification();

      myRegistrationResultSink.logChangedAndFullyBuiltIndices(
        FileBasedIndexImpl.LOG,
        "Indexes to be rebuilt after version change:",
        myCurrentVersionCorrupted ? "Indexes to be rebuilt after corruption:" : "Indices to be built:"
      );

      myState.freeze();
      FileBasedIndexDataInitializationResult result = new FileBasedIndexDataInitializationResult(myState, myCurrentVersionCorrupted, myOrphanDirtyFilesQueue);
      myRegisteredIndexes.setInitializationResult(result); // memory barrier
      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myState.getIndexIDs()) {
        try {
          RebuildStatus.clearIndexIfNecessary(indexId, () -> myFileBasedIndex.clearIndex(indexId));
        }
        catch (StorageException e) {
          myFileBasedIndex.requestRebuild(indexId, e);
          FileBasedIndexImpl.LOG.error(e);
        }
      }

      return result;
    }
    finally {
      //CorruptionMarker.markIndexesAsDirty();
      FileBasedIndexImpl.setupWritingIndexValuesSeparatedFromCounting();
      myFileBasedIndex.addStaleIds(myStaleIds);
      myFileBasedIndex.setUpFlusher();
      myRegisteredIndexes.ensureLoadedIndexesUpToDate();
      myRegisteredIndexes.markInitialized();  // this will ensure that all changes to component's state will be visible to other threads
      saveRegisteredIndicesAndDropUnregisteredOnes(myState.getIndexIDs());
    }
  }

  private void showChangedIndexesNotification() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || !Registry.is("ide.showIndexRebuildMessage", false)) {
      return;
    }

    String rebuildNotification;
    if (myCurrentVersionCorrupted) {
      rebuildNotification = IndexingBundle.message("index.corrupted.notification.text");
    }
    else if (myRegistrationResultSink.hasChangedIndexes()) {
      rebuildNotification = IndexingBundle.message("index.format.changed.notification.text", myRegistrationResultSink.changedIndices());
    }
    else {
      return;
    }

    NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
      .createNotification(IndexingBundle.message("index.rebuild.notification.title"), rebuildNotification, NotificationType.INFORMATION)
      .notify(null);
  }

  @Override
  protected @NotNull String getInitializationFinishedMessage(@NotNull FileBasedIndexDataInitializationResult initializationResult) {
    return "Initialized indexes: " + initializationResult.myState.getIndexIDs() + ".";
  }

  private static void saveRegisteredIndicesAndDropUnregisteredOnes(@NotNull Collection<? extends ID<?, ?>> ids) {
    if (ApplicationManager.getApplication().isDisposed() || !IndexInfrastructure.hasIndices()) {
      return;
    }

    final Path registeredIndicesFile = PathManager.getIndexRoot().resolve("registered");
    final Set<String> indicesToDrop = new HashSet<>();
    boolean exceptionThrown = false;
    if (Files.exists(registeredIndicesFile)) {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(registeredIndicesFile)))) {
        int size = in.readInt();
        for (int idx = 0; idx < size; idx++) {
          indicesToDrop.add(IOUtil.readString(in));
        }
      }
      catch (Throwable e) {
        // workaround for IDEA-194253
        LOG.info(e);
        exceptionThrown = true;
        ids.stream().map(ID::getName).forEach(indicesToDrop::add);
      }
    }

    boolean dropFilenameIndex = FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX &&
                                indicesToDrop.contains(FilenameIndex.NAME.getName());
    if (!exceptionThrown) {
      for (ID<?, ?> key : ids) {
        if (dropFilenameIndex && key == FilenameIndex.NAME) continue;
        indicesToDrop.remove(key.getName());
      }
    }

    if (!indicesToDrop.isEmpty()) {
      Collection<String> filtered = !dropFilenameIndex ? indicesToDrop :
                                    ContainerUtil.filter(indicesToDrop, o -> !FilenameIndex.NAME.getName().equals(o));
      if (!filtered.isEmpty()) LOG.info("Dropping indices:" + String.join(",", filtered));
      for (String s : indicesToDrop) {
        try {
          FileUtil.deleteWithRenaming(IndexInfrastructure.getFileBasedIndexRootDir(s).toFile());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    }

    try {
      Files.createDirectories(registeredIndicesFile.getParent());
      try (DataOutputStream os = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(registeredIndicesFile)))) {
        os.writeInt(ids.size());
        for (ID<?, ?> id : ids) {
          IOUtil.writeString(id.getName(), os);
        }
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static final class MyApplicationListener implements ApplicationListener {
    private final FileBasedIndexImpl myFileBasedIndex;

    MyApplicationListener(FileBasedIndexImpl fileBasedIndex) {
      myFileBasedIndex = fileBasedIndex;
    }

    @Override
    public void writeActionStarted(@NotNull Object action) {
      myFileBasedIndex.clearUpToDateIndexesForUnsavedOrTransactedDocs();
    }
  }

  static class FileBasedIndexDataInitializationResult {
    final IndexConfiguration myState;
    final boolean myWasCorrupted;
    final OrphanDirtyFilesQueue myOrphanDirtyFilesQueue;

    FileBasedIndexDataInitializationResult(@NotNull IndexConfiguration state, boolean currentVersionCorrupted, @NotNull OrphanDirtyFilesQueue queue) {
      myState = state;
      myWasCorrupted = currentVersionCorrupted;
      myOrphanDirtyFilesQueue = queue;
    }
  }
}
