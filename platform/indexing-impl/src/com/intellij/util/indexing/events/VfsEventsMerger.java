// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RollingFileHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@Internal
public final class VfsEventsMerger {
  private static final boolean DEBUG = FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES || Boolean.getBoolean("log.index.vfs.events");
  private static final Logger LOG = MyLoggerFactory.getLoggerInstance();

  static {
    if (LOG != null) {
      LOG.info("-------------- VfsEventsMerger initialized --------------------");
    }
  }

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

    /**
     * this is a helper method that designates the end of the events batch, can be used for optimizations
     */
    default void endBatch() {}
  }

  // 1. Method can be invoked in several threads
  // 2. Method processes snapshot of available events at the time of the invokation, it does mean that if events are produced concurrently
  // with the processing then set of events will be not empty
  // 3. Method regularly checks for cancellations (thus can finish with PCEs) but event processor should process the change info atomically
  // (without PCE)
  @VisibleForTesting
  public boolean processChanges(@NotNull VfsEventProcessor eventProcessor) {
    if (!myChangeInfos.isEmpty()) {
      Throwable interruptReason = null;

      try {
        int[] fileIds = myChangeInfos.keys(); // snapshot of the keys
        for (int fileId : fileIds) {
          ProgressManager.checkCanceled();
          ChangeInfo info = myChangeInfos.remove(fileId);
          if (info == null) continue;

          try {
            if (LOG != null) {
              LOG.info("Processing " + info);
            }
            if (!eventProcessor.process(info)) {
              eventProcessor.endBatch();
              return false;
            }
          }
          catch (ProcessCanceledException pce) { // todo remove (IJPL-9805)
            ((FileBasedIndexEx)FileBasedIndex.getInstance()).getLogger().error(new RuntimeException(pce));
            assert false;
          }
        }
        eventProcessor.endBatch();
      }
      catch (Throwable t) {
        interruptReason = t;
        throw t;
      }
      finally {
        if (LOG != null) {
          LOG.info("Processing " + (interruptReason != null ? "interrupted: " + interruptReason : "finished"));
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

  public @NotNull Iterator<VirtualFile> getChangedFiles() {
    return ContainerUtil.mapIterator(myChangeInfos.values().iterator(), ChangeInfo::getFile);
  }

  private final ConcurrentIntObjectMap<ChangeInfo> myChangeInfos =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  private static final short FILE_ADDED = 1;
  private static final short FILE_REMOVED = 2;
  private static final short FILE_CONTENT_CHANGED = 4;
  private static final short FILE_TRANSIENT_STATE_CHANGED = 8;

  @MagicConstant(flags = {FILE_ADDED, FILE_REMOVED, FILE_CONTENT_CHANGED, FILE_TRANSIENT_STATE_CHANGED})
  @interface EventMask { }

  @VisibleForTesting
  public static final class ChangeInfo {
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
      builder.append("file: ");
      if (file instanceof VirtualFileWithId fileWithId) {
        builder.append(fileWithId.getId());
      }
      else {
        builder.append(file.getPath());
      }
      builder.append("; ").append("operation: ");
      if ((eventMask & FILE_TRANSIENT_STATE_CHANGED) != 0) builder.append("TRANSIENT_STATE_CHANGE ");
      if ((eventMask & FILE_CONTENT_CHANGED) != 0) builder.append("CONTENT_CHANGE ");
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

  public static void tryLog(@NotNull String eventName, int fileId) {
    tryLog(() -> {
      return "e=" + eventName +
             ",id=" + fileId;
    });
  }

  public static void tryLog(@NotNull String eventName, @NotNull VirtualFile file, @Nullable Supplier<String> additionalMessage) {
    tryLog(() -> {
      return "e=" + eventName +
             (file instanceof VirtualFileWithId fileWithId ? (",id=" + fileWithId.getId()) : (",f=" + file.getPath())) +
             ",flen=" + file.getLength() +
             (additionalMessage == null ? "" : ("," + additionalMessage.get()));
    });
  }

  public static void tryLog(@NotNull String eventName, @NotNull IndexedFile indexedFile, @Nullable Supplier<String> additionalMessage) {
    VirtualFile file = indexedFile.getFile();

    tryLog(eventName, file, () -> {
      String extra = "f@" + System.identityHashCode(indexedFile);

      if (indexedFile instanceof FileContentImpl fileContentImpl) {
        extra += ",tr=" + (fileContentImpl.isTransientContent() ? "t" : "f");
      }

      if (indexedFile instanceof FileContent fileContent) {
        extra += ",contLen(b)=" + fileContent.getContent().length;
        FileType fileType = fileContent.getFileType();
        extra += ",psiLen=" + (fileType instanceof LanguageFileType ? fileContent.getPsiFile().getTextLength() : -1);
        extra += ",bin=" + (fileType.isBinary() ? "t" : "f");
      }

      if (additionalMessage != null) {
        extra += "," + additionalMessage.get();
      }
      return extra;
    });
  }

  public static void tryLog(Supplier<String> message) {
    if (LOG != null) {
      LOG.info(message.get());
    }
  }

  private static final class MyLoggerFactory implements Logger.Factory {
    private static final @Nullable MyLoggerFactory ourFactory;

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

    private final @NotNull RollingFileHandler myAppender;

    MyLoggerFactory() throws IOException {
      Path indexingDiagnosticDir = Paths.get(PathManager.getLogPath()).resolve("indexing-diagnostic");
      Path logPath = indexingDiagnosticDir.resolve("index-vfs-events.log");
      myAppender = new RollingFileHandler(logPath, 20000000, 50, false);
      myAppender.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          ZonedDateTime zdt = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());
          return String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL [%3$d] %2$s%n", zdt, record.getMessage(),
                               record.getLongThreadID());
        }
      });
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
