// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RollingFileHandler;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class VfsEventsMerger {
  private static final boolean DEBUG = FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE || Boolean.getBoolean("log.index.vfs.events");
  private static final Logger LOG = MyLoggerFactory.getLoggerInstance();

  void recordFileEvent(@NotNull VirtualFile file, boolean contentChange) {
    tryLog(contentChange ? "FILE_CONTENT_CHANGED" : "FILE_ADDED", file);
    updateChange(file, contentChange ? FILE_CONTENT_CHANGED : FILE_ADDED);
  }

  void recordFileRemovedEvent(@NotNull VirtualFile file) {
    tryLog("FILE_REMOVED", file);
    updateChange(file, FILE_REMOVED);
  }

  void recordTransientStateChangeEvent(@NotNull VirtualFile file) {
    tryLog("FILE_TRANSIENT_STATE_CHANGED", file);
    updateChange(file, FILE_TRANSIENT_STATE_CHANGED);
  }

  private final AtomicInteger myPublishedEventIndex = new AtomicInteger();

  int getPublishedEventIndex() {
    return myPublishedEventIndex.get();
  }

  private void updateChange(@NotNull VirtualFile file, @EventMask short mask) {
    if (file instanceof VirtualFileWithId) {
      updateChange(((VirtualFileWithId)file).getId(), file, mask);
    }
  }

  // NB: this code is executed not only during vfs events dispatch (in write action) but also during requestReindex (in read action)
  private void updateChange(int fileId, @NotNull VirtualFile file, @EventMask short mask) {
    while (true) {
      ChangeInfo existingChangeInfo = myChangeInfos.get(fileId);
      ChangeInfo newChangeInfo = new ChangeInfo(file, mask, existingChangeInfo);
      if(myChangeInfos.put(fileId, newChangeInfo) == existingChangeInfo) {
        myPublishedEventIndex.incrementAndGet();
        break;
      }
    }
  }

  @FunctionalInterface
  public interface VfsEventProcessor {
    boolean process(@NotNull ChangeInfo changeInfo);
  }

  // 1. Method can be invoked in several threads
  // 2. Method processes snapshot of available events at the time of the invokation, it does mean that if events are produced concurrently
  // with the processing then set of events will be not empty
  // 3. Method regularly checks for cancellations (thus can finish with PCEs) but event processor should process the change info atomically
  // (without PCE)
  boolean processChanges(@NotNull VfsEventProcessor eventProcessor) {
    if (!myChangeInfos.isEmpty()) {
      int[] fileIds = myChangeInfos.keys(); // snapshot of the keys
      for (int fileId : fileIds) {
        ProgressManager.checkCanceled();
        ChangeInfo info = myChangeInfos.remove(fileId);
        if (info == null) continue;

        try {
          if (LOG != null) {
            LOG.info("Processing " + info);
          }
          if (!eventProcessor.process(info)) return false;
        }
        catch (ProcessCanceledException pce) { // todo remove
          ((FileBasedIndexEx)FileBasedIndex.getInstance()).getLogger().error(pce);
          assert false;
        }
      }
    }
    return true;
  }

  boolean hasChanges() {
    return !myChangeInfos.isEmpty();
  }

  int getApproximateChangesCount() {
    return myChangeInfos.size();
  }

  @NotNull
  public Stream<VirtualFile> getChangedFiles() {
    return myChangeInfos.values().stream().map(ChangeInfo::getFile);
  }

  private final ConcurrentIntObjectMap<ChangeInfo> myChangeInfos =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  private static final short FILE_ADDED = 1;
  private static final short FILE_REMOVED = 2;
  private static final short FILE_CONTENT_CHANGED = 4;
  private static final short FILE_TRANSIENT_STATE_CHANGED = 8;

  @MagicConstant(flags = {FILE_ADDED, FILE_REMOVED, FILE_CONTENT_CHANGED, FILE_TRANSIENT_STATE_CHANGED})
  @interface EventMask { }

  static class ChangeInfo {
    private final VirtualFile file;

    @EventMask
    private final short eventMask;

    ChangeInfo(@NotNull VirtualFile file, @EventMask short eventMask, @Nullable ChangeInfo previous) {
      this.file = file;
      this.eventMask = mergeEventMask(previous == null ? 0 : previous.eventMask, eventMask);
    }

    @EventMask
    private static short mergeEventMask(@EventMask short existingOperation, @EventMask short newOperation) {
      if (newOperation == FILE_REMOVED) {
        return FILE_REMOVED;
      }
      return (short)(existingOperation | newOperation);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("file: ").append(file.getPath()).append("; ")
        .append("operation: ");
      if ((eventMask & FILE_TRANSIENT_STATE_CHANGED) != 0) builder.append("TRANSIENT_STATE_CHANGE ");
      if ((eventMask & FILE_CONTENT_CHANGED) != 0) builder.append("UPDATE ");
      if ((eventMask & FILE_REMOVED) != 0) builder.append("REMOVE ");
      if ((eventMask & FILE_ADDED) != 0) builder.append("ADD ");
      return builder.toString().trim();
    }

    boolean isContentChanged() {
      return (eventMask & FILE_CONTENT_CHANGED) != 0;
    }

    boolean isFileRemoved() {
      return (eventMask & FILE_REMOVED) != 0;
    }

    boolean isFileAdded() {
      return (eventMask & FILE_ADDED) != 0;
    }

    boolean isTransientStateChanged() {
      return (eventMask & FILE_TRANSIENT_STATE_CHANGED) != 0;
    }

    @NotNull
    VirtualFile getFile() {
      return file;
    }

    int getFileId() {
      int fileId = FileBasedIndex.getFileId(file);
      assert fileId >= 0;
      return fileId;
    }
  }

  public static void tryLog(@NotNull String eventName, @NotNull VirtualFile file) {
    tryLog(eventName, file, null);
  }

  public static void tryLog(@NotNull String eventName, @NotNull VirtualFile file, @Nullable Supplier<String> additionalMessage) {
    tryLog(() -> {
      return "event=" + eventName +
             ",f=" + file.getPath() +
             (file instanceof VirtualFileWithId ? (",id=" + ((VirtualFileWithId)file).getId()) : "") +
             (additionalMessage == null ? "" : ("," + additionalMessage.get()));
    });
  }

  public static void tryLog(Supplier<String> message) {
    if (LOG != null) {
      LOG.info(message.get());
    }
  }

  private static class MyLoggerFactory implements Logger.Factory {
    @Nullable
    private static final MyLoggerFactory ourFactory;

    static {
      MyLoggerFactory factory = null;
      try {
        if (DEBUG) {
          factory = new MyLoggerFactory();
        }
      }
      catch (IOException e) {
        ((FileBasedIndexEx)FileBasedIndex.getInstance()).getLogger().error(e);
      }
      ourFactory = factory;
    }

    @NotNull
    private final RollingFileHandler myAppender;

    MyLoggerFactory() throws IOException {
      Path indexingDiagnosticDir = Paths.get(PathManager.getLogPath()).resolve("indexing-diagnostic");
      Path logPath = indexingDiagnosticDir.resolve("index-vfs-events.log");
      myAppender = new RollingFileHandler(logPath, 20000000, 50, false);
    }

    @Override
    public @NotNull Logger getLoggerInstance(@NotNull String category) {
      final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(category);
      JulLogger.clearHandlers(logger);
      logger.addHandler(myAppender);
      logger.setUseParentHandlers(false);
      logger.setLevel(Level.INFO);
      return new JulLogger(logger);
    }

    public static @Nullable Logger getLoggerInstance() {
      return ourFactory == null ? null : ourFactory.getLoggerInstance("#" + VfsEventsMerger.class.getName());
    }
  }
}
