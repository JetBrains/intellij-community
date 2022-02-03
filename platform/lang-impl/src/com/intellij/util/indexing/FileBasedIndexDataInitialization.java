// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayout;
import com.intellij.util.indexing.impl.storage.FileBasedIndexLayoutSettings;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.serviceContainer.ComponentManagerImplKt.handleComponentError;

final class FileBasedIndexDataInitialization extends IndexDataInitializer<IndexConfiguration> {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexDataInitialization.class);

  private boolean myCurrentVersionCorrupted;

  @NotNull
  private final FileBasedIndexImpl myFileBasedIndex;
  @NotNull
  private final RegisteredIndexes myRegisteredIndexes;
  @NotNull
  private final IntSet myStaleIds = IntSets.synchronize(new IntOpenHashSet());
  @NotNull
  private final IndexVersionRegistrationSink myRegistrationResultSink = new IndexVersionRegistrationSink();
  @NotNull
  private final IndexConfiguration myState = new IndexConfiguration();

  FileBasedIndexDataInitialization(@NotNull FileBasedIndexImpl index, @NotNull RegisteredIndexes registeredIndexes) {
    myFileBasedIndex = index;
    myRegisteredIndexes = registeredIndexes;
  }

  private @NotNull Collection<ThrowableRunnable<?>> initAssociatedDataForExtensions() {
    Activity activity = StartUpMeasurer.startActivity("file index extensions iteration");
    ExtensionPointImpl<FileBasedIndexExtension<?, ?>> extPoint =
      (ExtensionPointImpl<FileBasedIndexExtension<?, ?>>)FileBasedIndexExtension.EXTENSION_POINT_NAME.getPoint();
    Iterator<FileBasedIndexExtension<?, ?>> extensions = extPoint.iterator();
    List<ThrowableRunnable<?>> tasks = new ArrayList<>(extPoint.size());

    // todo: init contentless indices first ?
    while (extensions.hasNext()) {
      FileBasedIndexExtension<?, ?> extension = extensions.next();
      if (extension == null) {
        break;
      }
      RebuildStatus.registerIndex(extension.getName());

      myRegisteredIndexes.registerIndexExtension(extension);

      tasks.add(() -> {
        try {
          FileBasedIndexImpl.registerIndexer(extension,
                                             myState,
                                             myRegistrationResultSink,
                                             myStaleIds);
        }
        catch (IOException io) {
          throw io;
        }
        catch (Throwable t) {
          handleComponentError(t, extension.getClass().getName(), null);
        }
      });
    }

    myRegisteredIndexes.extensionsDataWasLoaded();
    activity.end();

    return tasks;
  }

  @Override
  protected @NotNull Collection<ThrowableRunnable<?>> prepareTasks() {
    // PersistentFS lifecycle should contain FileBasedIndex lifecycle, so,
    // 1) we call for it's instance before index creation to make sure it's initialized
    // 2) we dispose FileBasedIndex before PersistentFS disposing
    PersistentFSImpl fs = (PersistentFSImpl)ManagingFS.getInstance();
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    Disposable disposable = () -> new FileBasedIndexImpl.MyShutDownTask().run();
    ApplicationManager.getApplication().addApplicationListener(new MyApplicationListener(fileBasedIndex), disposable);
    Disposer.register(fs, disposable);
    myFileBasedIndex.setUpShutDownTask();

    Collection<ThrowableRunnable<?>> tasks = initAssociatedDataForExtensions();

    PersistentIndicesConfiguration.loadConfiguration();

    myCurrentVersionCorrupted = CorruptionMarker.requireInvalidation();
    boolean storageLayoutChanged = FileBasedIndexLayoutSettings.INSTANCE.loadUsedLayout();
    for (FileBasedIndexInfrastructureExtension ex : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensions()) {
      FileBasedIndexInfrastructureExtension.InitializationResult result = ex.initialize(DefaultIndexStorageLayout.getUsedLayoutId());
      myCurrentVersionCorrupted = myCurrentVersionCorrupted ||
                                result == FileBasedIndexInfrastructureExtension.InitializationResult.INDEX_REBUILD_REQUIRED;
    }
    myCurrentVersionCorrupted = myCurrentVersionCorrupted || storageLayoutChanged;

    if (myCurrentVersionCorrupted) {
      CorruptionMarker.dropIndexes();
    }

    return tasks;
  }

  @Override
  protected @NotNull IndexConfiguration finish() {
    try {
      myState.finalizeFileTypeMappingForIndices();

      showChangedIndexesNotification();

      myRegistrationResultSink.logChangedAndFullyBuiltIndices(
        FileBasedIndexImpl.LOG,
        "Indexes to be rebuilt after version change:",
        myCurrentVersionCorrupted ? "Indexes to be rebuilt after corruption:" : "Indices to be built:"
      );

      myState.freeze();
      myRegisteredIndexes.setState(myState); // memory barrier
      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myState.getIndexIDs()) {
        try {
          RebuildStatus.clearIndexIfNecessary(indexId, () -> myFileBasedIndex.clearIndex(indexId));
        }
        catch (StorageException e) {
          myFileBasedIndex.requestRebuild(indexId);
          FileBasedIndexImpl.LOG.error(e);
        }
      }

      return myState;
    }
    finally {
      //CorruptionMarker.markIndexesAsDirty();
      myFileBasedIndex.addStaleIds(myStaleIds);
      myFileBasedIndex.setUpFlusher();
      myFileBasedIndex.setUpHealthCheck();
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

  @NotNull
  @Override
  protected String getInitializationFinishedMessage(@NotNull IndexConfiguration initializationResult) {
    return "Initialized indexes: " + initializationResult.getIndexIDs() + ".";
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

    if (!exceptionThrown) {
      for (ID<?, ?> key : ids) {
        indicesToDrop.remove(key.getName());
      }
    }

    if (!indicesToDrop.isEmpty()) {
      LOG.info("Dropping indices:" + String.join(",", indicesToDrop));
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
}
