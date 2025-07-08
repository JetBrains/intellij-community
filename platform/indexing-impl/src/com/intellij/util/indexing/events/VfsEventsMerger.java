// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

@Internal
public final class VfsEventsMerger {

  @ApiStatus.Internal
  public void recordFileEvent(@NotNull VirtualFile file, boolean contentChange) {
    IndexingEventsLogger.tryLog(contentChange ? "FILE_CONTENT_CHANGED" : "FILE_ADDED", file);
    updateChange(file, contentChange ? FILE_CONTENT_CHANGED : FILE_ADDED);
  }

  @ApiStatus.Internal
  public void recordFileRemovedEvent(@NotNull VirtualFile file) {
    IndexingEventsLogger.tryLog("FILE_REMOVED", file);
    updateChange(file, FILE_REMOVED);
  }

  @ApiStatus.Internal
  public void recordTransientStateChangeEvent(@NotNull VirtualFile file) {
    IndexingEventsLogger.tryLog("FILE_TRANSIENT_STATE_CHANGED", file);
    updateChange(file, FILE_TRANSIENT_STATE_CHANGED);
  }

  private final AtomicInteger myPublishedEventIndex = new AtomicInteger();

  @ApiStatus.Internal
  public int getPublishedEventIndex() {
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
            IndexingEventsLogger.tryLog(() -> "Processing " + info);

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
        final Throwable finalInterruptReason = interruptReason;
        IndexingEventsLogger.tryLog(() -> {
          return "Processing " + (finalInterruptReason != null ? "interrupted: " + finalInterruptReason : "finished");
        });
      }
    }
    return true;
  }

  @ApiStatus.Internal
  public boolean hasChanges() {
    return !myChangeInfos.isEmpty();
  }

  @ApiStatus.Internal
  public int getApproximateChangesCount() {
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

    @ApiStatus.Internal
    public boolean isContentChanged() {
      return (eventMask & FILE_CONTENT_CHANGED) != 0;
    }

    @ApiStatus.Internal
    public boolean isFileRemoved() {
      return (eventMask & FILE_REMOVED) != 0;
    }

    @ApiStatus.Internal
    public boolean isFileAdded() {
      return (eventMask & FILE_ADDED) != 0;
    }

    @ApiStatus.Internal
    public boolean isTransientStateChanged() {
      return (eventMask & FILE_TRANSIENT_STATE_CHANGED) != 0;
    }

    @NotNull
    @ApiStatus.Internal
    public VirtualFile getFile() {
      return file;
    }

    @ApiStatus.Internal
    public int getFileId() {
      int fileId = FileBasedIndex.getFileId(file);
      assert fileId >= 0;
      return fileId;
    }
  }
}
