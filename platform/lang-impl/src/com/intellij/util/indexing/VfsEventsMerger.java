// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

final class VfsEventsMerger {
  private static final boolean DEBUG = FileBasedIndexImpl.DO_TRACE_STUB_INDEX_UPDATE || SystemProperties.is("log.index.vfs.events");
  @Nullable
  static final Logger LOG = MyLoggerFactory.getLoggerInstance();

  void recordFileEvent(@NotNull VirtualFile file, boolean contentChange) {
    if (LOG != null) LOG.info("Request build indices for file:" + file.getPath() + ", contentChange:" + contentChange);
    updateChange(file, contentChange ? FILE_CONTENT_CHANGED : FILE_ADDED);
  }

  void recordBeforeFileEvent(@NotNull VirtualFile file, boolean contentChanged) {
    if (LOG != null) LOG.info("Request invalidate indices for file:" + file.getPath() + ", contentChange:" + contentChanged);
    updateChange(file, contentChanged ? BEFORE_FILE_CONTENT_CHANGED : FILE_REMOVED);
  }

  void recordTransientStateChangeEvent(@NotNull VirtualFile file) {
    if (LOG != null) LOG.info("Transient state changed for file:" + file.getPath());
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

  void applyMergedEvents(@NotNull VfsEventsMerger merger) {
    for(ChangeInfo info:merger.myChangeInfos.values()) {
      updateChange(info.getFileId(), info.file, info.eventMask);
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
          FileBasedIndexImpl.LOG.error(pce);
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
  Stream<VirtualFile> getChangedFiles() {
    return myChangeInfos.values().stream().map(ChangeInfo::getFile);
  }

  private final ConcurrentIntObjectMap<ChangeInfo> myChangeInfos = ContainerUtil.createConcurrentIntObjectMap();

  private static final short FILE_ADDED = 1;
  private static final short FILE_REMOVED = 2;
  private static final short FILE_CONTENT_CHANGED = 4;
  private static final short BEFORE_FILE_CONTENT_CHANGED = 8;
  private static final short FILE_TRANSIENT_STATE_CHANGED = 16;

  @MagicConstant(flags = {FILE_ADDED, FILE_REMOVED, FILE_CONTENT_CHANGED, BEFORE_FILE_CONTENT_CHANGED, FILE_TRANSIENT_STATE_CHANGED})
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
      if ((eventMask & BEFORE_FILE_CONTENT_CHANGED) != 0) builder.append("UPDATE-REMOVE ");
      if ((eventMask & FILE_CONTENT_CHANGED) != 0) builder.append("UPDATE ");
      if ((eventMask & FILE_REMOVED) != 0) builder.append("REMOVE ");
      if ((eventMask & FILE_ADDED) != 0) builder.append("ADD ");
      return builder.toString().trim();
    }

    boolean isBeforeContentChanged() {
      return (eventMask & BEFORE_FILE_CONTENT_CHANGED) != 0;
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
      int fileId = FileBasedIndexImpl.getIdMaskingNonIdBasedFile(file);
      if (fileId < 0) fileId = -fileId;
      return fileId;
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
        FileBasedIndexImpl.LOG.error(e);
      }
      ourFactory = factory;
    }

    @NotNull
    private final RollingFileAppender myAppender;

    MyLoggerFactory() throws IOException {
      Path logPath = IndexDiagnosticDumper.INSTANCE.getIndexingDiagnosticDir().resolve("index-vfs-events.log");
      PatternLayout pattern = new PatternLayout("%d [%7r] %6p - %m\n");
      myAppender = new RollingFileAppender(pattern, logPath.toFile().getAbsolutePath());
      myAppender.setMaxFileSize("20MB");
      myAppender.setMaxBackupIndex(50);
    }


    @Override
    public @NotNull Logger getLoggerInstance(@NotNull String category) {
      final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(category);
      logger.removeAllAppenders();
      logger.addAppender(myAppender);
      logger.setAdditivity(false);
      logger.setLevel(Level.INFO);
      return new Log4jBasedLogger(logger);
    }

    public static @Nullable Logger getLoggerInstance() {
      return ourFactory == null ? null : ourFactory.getLoggerInstance("#" + VfsEventsMerger.class.getName());
    }
  }
}
