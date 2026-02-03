// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;
import static com.intellij.util.indexing.events.VfsEventsMerger.ChangeInfo.*;

/**
 * Accumulates VFS file-change events [file, change: (ADDED | REMOVED | CONTENT_CHANGED | TRANSIENT_STATE_CHANGED)]
 * and merges same-file events into a single record.
 * </p>
 * Accumulated changes could then be consumed with {@link #processChanges(VfsEventProcessor)}
 */
@Internal
public final class VfsEventsMerger {

  /** Map[fileId -> ChangeInfo] */
  private final ConcurrentIntObjectMap<ChangeInfo> changePerFileId = createConcurrentIntObjectMap();

  /**
   * Number of file events applied (='published') to {@link #changePerFileId}, since start. Could be used to find out
   * are there any new changes _since_ the last access
   */
  private final AtomicInteger publishedEventIndex = new AtomicInteger();

  public void recordFileEvent(@NotNull VirtualFile file, boolean contentChange) {
    IndexingEventsLogger.tryLog(contentChange ? "FILE_CONTENT_CHANGED" : "FILE_ADDED", file);
    updateChange(file, contentChange ? FILE_CONTENT_CHANGED : FILE_ADDED);
  }

  public void recordFileRemovedEvent(@NotNull VirtualFile file) {
    IndexingEventsLogger.tryLog("FILE_REMOVED", file);
    updateChange(file, FILE_REMOVED);
  }

  public void recordTransientStateChangeEvent(@NotNull VirtualFile file) {
    IndexingEventsLogger.tryLog("FILE_TRANSIENT_STATE_CHANGED", file);
    updateChange(file, ChangeInfo.FILE_TRANSIENT_STATE_CHANGED);
  }

  /**
   * Number of file events applied (='published') to {@link #changePerFileId}, since start. Could be used to find out
   * are there any new changes _since_ the last access
   */
  public int getPublishedEventIndex() {
    return publishedEventIndex.get();
  }

  private void updateChange(@NotNull VirtualFile file, @EventMask int mask) {
    if (file instanceof VirtualFileWithId) {
      updateChange(((VirtualFileWithId)file).getId(), file, mask);
    }
  }

  // NB: this code is executed not only during vfs events dispatch (in write action) but also during requestReindex (in read action)
  private void updateChange(int fileId, @NotNull VirtualFile file, @EventMask int mask) {
    while (true) {// CAS-like loop:
      ChangeInfo existingChangeInfo = changePerFileId.get(fileId);
      if (existingChangeInfo != null && existingChangeInfo.changeMask() == mask) {
        return;//nothing to update
      }

      ChangeInfo newChangeInfo = new ChangeInfo(file, mask, existingChangeInfo);
      if (existingChangeInfo == null) { //.replace() impl doesn't support oldValue=null, hence the branch:
        if (changePerFileId.putIfAbsent(fileId, newChangeInfo) == null) {
          break;
        }
      }
      else {
        if (changePerFileId.replace(fileId, existingChangeInfo, newChangeInfo)) {
          break;
        }
      }
    }
    publishedEventIndex.incrementAndGet();
  }

  @FunctionalInterface
  public interface VfsEventProcessor {
    boolean process(@NotNull ChangeInfo changeInfo);

    /** this is a helper method that designates the end of the events batch, can be used for optimizations */
    default void endBatch() { }
  }

  /**
   * 1. Method can be invoked in several threads.
   * 2. Method processes the snapshot of available events at the time of the invocation: it means that if events are produced
   *    concurrently with their processing, then the set of events _could_ be non-empty after the method terminates.
   * 3. Method itself regularly checks for cancellations (thus _can_ finish with PCEs), but event processor should process the
   *    change info atomically (without PCE)
   */
  @VisibleForTesting
  public boolean processChanges(@NotNull VfsEventProcessor eventProcessor) {
    if (!changePerFileId.isEmpty()) {
      Throwable interruptReason = null;

      try {
        int[] fileIds = changePerFileId.keys(); // snapshot of the keys
        for (int fileId : fileIds) {
          ProgressManager.checkCanceled();
          ChangeInfo info = changePerFileId.remove(fileId);
          if (info == null) continue;

          try {
            IndexingEventsLogger.tryLog(() -> "Processing " + info);

            if (!eventProcessor.process(info)) {
              eventProcessor.endBatch();
              return false;
            }
          }
          catch (AlreadyDisposedException e) {
            throw e;
          }
          catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException pce) {
            //IJPL-9805: it should be no PCE here -- eventProcessor.process()/.endBatch() should
            //           be 'atomic': a change is either processed, or not, so throw PCE from inside
            //           the processor is an error
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

  public boolean hasChanges() {
    return !changePerFileId.isEmpty();
  }

  //why 'approximate'?
  public int getApproximateChangesCount() {
    return changePerFileId.size();
  }

  public @NotNull Iterator<VirtualFile> getChangedFiles() {
    return ContainerUtil.mapIterator(changePerFileId.values().iterator(), ChangeInfo::getFile);
  }

  @VisibleForTesting
  public static final class ChangeInfo {
    //@formatter:off
    public static final int FILE_ADDED                     = 0b0001;
    public static final int FILE_REMOVED                   = 0b0010;
    public static final int FILE_CONTENT_CHANGED           = 0b0100;
    public static final int FILE_TRANSIENT_STATE_CHANGED   = 0b1000;

    @MagicConstant(flags = {FILE_ADDED, FILE_REMOVED, FILE_CONTENT_CHANGED, FILE_TRANSIENT_STATE_CHANGED})
    @interface EventMask {}
    //@formatter:on

    private final VirtualFile file;

    private final @EventMask int changeMask;

    ChangeInfo(@NotNull VirtualFile file,
               @EventMask int changeMask,
               @Nullable ChangeInfo previous) {
      this.file = file;
      this.changeMask = mergeEventMask(previous == null ? 0 : previous.changeMask, changeMask);
    }

    private static @EventMask int mergeEventMask(@EventMask int existingMask,
                                                 @EventMask int newMask) {
      if (newMask == FILE_REMOVED) {
        return FILE_REMOVED;
      }
      return existingMask | newMask;
    }

    public boolean isContentChanged() {
      return (changeMask & FILE_CONTENT_CHANGED) != 0;
    }

    public boolean isFileRemoved() {
      return (changeMask & FILE_REMOVED) != 0;
    }

    public boolean isFileAdded() {
      return (changeMask & FILE_ADDED) != 0;
    }

    public boolean isTransientStateChanged() {
      return (changeMask & FILE_TRANSIENT_STATE_CHANGED) != 0;
    }

    public @EventMask int changeMask(){
      return changeMask;
    }

    public @NotNull VirtualFile getFile() {
      return file;
    }

    public int getFileId() {
      return FileBasedIndex.getFileId(file);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("[file: ");
      if (file instanceof VirtualFileWithId fileWithId) {
        builder.append(fileWithId.getId());
      }
      else {
        builder.append(file.getPath());
      }
      builder.append(", ").append("operation: {");
      if (isTransientStateChanged()) builder.append("TRANSIENT_STATE_CHANGE ");
      if (isContentChanged()) builder.append("CONTENT_CHANGE ");
      if (isFileRemoved()) builder.append("REMOVE ");
      if (isFileAdded()) builder.append("ADD ");
      return builder.append("}]").toString();
    }
  }
}
