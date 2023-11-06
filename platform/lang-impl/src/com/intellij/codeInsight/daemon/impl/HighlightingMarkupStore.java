// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;

import static com.intellij.codeInsight.daemon.impl.HighlightingMarkupGrave.FileMarkupInfo;

/**
 * Persistent thread-safe store for markup highlighters. It is not allowed to interact with the store on EDT.
 *
 * <p> Essentially, this is a map (project, virtualFile) -> rangeHighlighters.
 * In-memory state is flushed on disk every second to system/persistent-markup directory.
 * The persistent map will be recreated under the hood in case of IO exceptions (e.g. markup corrupted).
 */
final class HighlightingMarkupStore {
  private static final @NotNull Logger LOG = Logger.getInstance(HighlightingMarkupStore.class);
  private static final @NotNull String PERSISTENT_MARKUP = "persistent-markup";
  private static final @NotNull ExecutorService EXECUTOR_SERVICE = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("MarkupStorePool");
  private static final @NotNull Set<@NotNull HighlightingMarkupStore> ALL_STORES = ConcurrentCollectionFactory.createConcurrentSet();

  static {
    ShutDownTracker.getInstance().registerCacheShutdownTask(() -> ALL_STORES.forEach(store -> store.close(true)));
  }

  private final @NotNull String storeName;
  private volatile PersistentMapBase<@NotNull Integer, @NotNull FileMarkupInfo> fileIdToMarkupMap;
  private volatile ScheduledFuture<?> scheduledFlushing;

  @RequiresBackgroundThread
  static @NotNull HighlightingMarkupStore create(@NotNull Project project) {
    String storeName = trimLongString(project.getName()) + "-" + trimLongString(project.getLocationHash());
    var persistentMap = createPersistentMap(storeName, getStorePath(storeName));
    HighlightingMarkupStore markupStore = new HighlightingMarkupStore(storeName, persistentMap);
    markupStore.flushOnLowMemory(project);
    ALL_STORES.add(markupStore);
    return markupStore;
  }

  private HighlightingMarkupStore(@NotNull String storeName, PersistentMapBase<@NotNull Integer, @NotNull FileMarkupInfo> fileIdToMarkupMap) {
    this.storeName = storeName;
    this.fileIdToMarkupMap = fileIdToMarkupMap;
    this.scheduledFlushing = startPeriodicallyFlushing();
  }

  @RequiresBackgroundThread
  void putMarkup(@NotNull VirtualFileWithId file, @NotNull FileMarkupInfo markupInfo) {
    if (!isEnabled()) {
      return;
    }
    try {
      fileIdToMarkupMap.put(file.getId(), markupInfo);
    }
    catch (IOException e) {
      LOG.warn("cannot store markup " + markupInfo + " for file " + file, e);
    }
  }

  @RequiresBackgroundThread
  @Nullable FileMarkupInfo getMarkup(@NotNull VirtualFileWithId file) {
    if (!isEnabled()) {
      return null;
    }
    try {
      return fileIdToMarkupMap.get(file.getId());
    }
    catch (IOException e) {
      LOG.warn("cannot get markup for file " + file, e);
    }
    // try to remove only one corrupted markup
    removeMarkup(file);
    return null;
  }

  @RequiresBackgroundThread
  void removeMarkup(@NotNull VirtualFileWithId file) {
    if (!isEnabled()) {
      return;
    }
    try {
      fileIdToMarkupMap.remove(file.getId());
    }
    catch (IOException e) {
      handleIOException(e);
    }
  }

  @RequiresBackgroundThread(generateAssertion = false) // TODO: enable when grave closes on BG thread
  void close(boolean isAppShutDown) {
    if (!isEnabled()) {
      return;
    }
    if (!isAppShutDown) {
      ALL_STORES.remove(this);
    }
    scheduledFlushing.cancel(false);
    debugLogContent();
    try {
      fileIdToMarkupMap.close();
    }
    catch (IOException e) {
      LOG.warn("error on persistent map close", e);
    }
  }

  static @NotNull ExecutorService getExecutor() {
    return EXECUTOR_SERVICE;
  }

  private void flushOnLowMemory(@NotNull Disposable parentDisposable) {
    if (!isEnabled()) {
      return;
    }
    LowMemoryWatcher.register(this::flushOnDisk, parentDisposable);
  }

  /**
   * Store is disabled in case of unhandled errors, silently continue to work as no op
   */
  private boolean isEnabled() {
    return fileIdToMarkupMap != null;
  }

  private @Nullable ScheduledFuture<?> startPeriodicallyFlushing() {
    if (!isEnabled()) {
      return null;
    }
    return FlushingDaemon.runPeriodically(this::flushOnDisk);
  }

  private void flushOnDisk() {
    if (!isEnabled()) {
      return;
    }
    try {
      fileIdToMarkupMap.force();
    }
    catch (IOException e) {
      LOG.info("error while flushing persistent map on disk", e);
    }
  }

  private void handleIOException(@NotNull IOException exception) {
    LOG.info("recreating persistent map due to error", exception);
    scheduledFlushing.cancel(false);
    try {
      fileIdToMarkupMap.closeAndDelete();
    }
    catch (IOException e) {
      LOG.warn("error on close markup cache", e);
    }
    fileIdToMarkupMap = createPersistentMap(storeName, getStorePath(storeName));
    scheduledFlushing = startPeriodicallyFlushing();
  }

  private void debugLogContent() {
    if (!LOG.isDebugEnabled()) {
      return;
    }
    Processor<Integer> markupPrinter = fileId -> {
      VirtualFile file = VirtualFileManager.getInstance().findFileById(fileId);
      if (file instanceof VirtualFileWithId fileWithId) {
        FileMarkupInfo markupInfo;
        try {
          markupInfo = fileIdToMarkupMap.get(fileWithId.getId());
        }
        catch (IOException e) {
          LOG.debug("error on content debug of persistent map", e);
          return false;
        }
        if (markupInfo != null) {
          LOG.debug(this + " " + markupInfo.size() + " highlighters for " + file.getName());
        }
      }
      return true;
    };
    try {
      fileIdToMarkupMap.processExistingKeys(markupPrinter);
    }
    catch (IOException e) {
      LOG.debug("error on content debug of persistent map", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HighlightingMarkupStore store = (HighlightingMarkupStore)o;
    return storeName.equals(store.storeName);
  }

  @Override
  public int hashCode() {
    return storeName.hashCode();
  }

  @Override
  public @NotNull String toString() {
    String size = isEnabled() ? fileIdToMarkupMap.keysCount() + " files" : "disabled";
    return "HighlightingMarkupStore[" + storeName + ", " + size + ']';
  }

  private static @Nullable PersistentMapImpl<Integer, FileMarkupInfo> createPersistentMap(@NotNull String name, @NotNull Path path) {
    var mapBuilder = PersistentMapBuilder.newBuilder(path, EnumeratorIntegerDescriptor.INSTANCE, FileMarkupInfoExternalizer.INSTANCE)
      .withVersion(2);
    PersistentMapImpl<Integer, FileMarkupInfo> map = null;
    Exception exception = null;
    int retryAttempts = 5;
    for (int i = 0; i < retryAttempts; i++) {
      if (i > 1) {
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException ignored) {}
      }
      try {
        map = new PersistentMapImpl<>(mapBuilder);
        break;
      }
      catch (VersionUpdatedException e) {
        LOG.info("markup persistent map " + e.getMessage() + ", attempt " + i);
        exception = e;
        IOUtil.deleteAllFilesStartingWith(path);
      }
      catch (Exception e) {
        LOG.warn("error while creating persistent map, attempt " + i, e);
        exception = e;
        IOUtil.deleteAllFilesStartingWith(path);
      }
    }
    if (map == null) {
      LOG.error("cannot create persistent map", exception);
      return null;
    }
    LOG.info("restoring markup cache '" + name + "' for " + map.getSize() + " files");
    return map;
  }

  private static @NotNull Path getStorePath(@NotNull String storeName) {
    return PathManager.getSystemDir().resolve(PERSISTENT_MARKUP).resolve(storeName);
  }

  private static @NotNull String trimLongString(@NotNull String string) {
    return StringUtil.shortenTextWithEllipsis(string, 50, 10, "")
      .replaceAll("[^\\p{IsAlphabetic}\\d]", "")
      .replace(" ", "")
      .replace(StringUtil.NON_BREAK_SPACE, "");
  }

  private static final class FileMarkupInfoExternalizer implements DataExternalizer<@NotNull FileMarkupInfo> {

    static final @NotNull FileMarkupInfoExternalizer INSTANCE = new FileMarkupInfoExternalizer();

    @Override
    public void save(@NotNull DataOutput out, @NotNull FileMarkupInfo value) throws IOException {
      value.bury(out);
    }

    @Override
    public @NotNull FileMarkupInfo read(@NotNull DataInput in) throws IOException {
      return FileMarkupInfo.exhume(in);
    }
  }
}
