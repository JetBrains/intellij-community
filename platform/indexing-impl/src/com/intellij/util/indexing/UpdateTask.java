// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/// Coordinates processing ([#processSerializable(Object, Project)]) of items passed to [#processAll(Collection, Project)] in a multithreading environment so that:
/// 1. processing of the same item by different threads is always **serialized**, never concurrent
///    (if all threads are using the same instance of [UpdateTask]!)
/// 2. different items **could** be processed concurrently by different threads
/// 3. **all** items passed in [#processAll(Collection, Project)] are eventually processed by this thread
/// 
@ApiStatus.Internal
public abstract class UpdateTask<Item> {
  /// Semaphore is used in an unusual way: as a counter of active operations, with option to wait for no active operations.
  /// Each attempt to process an item starts with .down(), and ends with .up(), so .waitFor() terminates when there is
  /// 0 attempts currently in progress -- which is when a repeating attempt has more chances to succeed.
  private final Semaphore inProgressOperations = new Semaphore();

  private final Set<Item> itemsBeingProcessed = ConcurrentCollectionFactory.createConcurrentSet();

  /// Prevents a nested processAll() call from waiting for an operation that can finish only after the nested call returns.
  private final ThreadLocal<Boolean> processingOnCurrentThread = new ThreadLocal<>();


  //TODO RC: `Collection itemsToProcess` is an ambiguous semantics -- it allows collections with duplicates [a,...a,...], but
  //         it is not clear how to deal with them: should we process the same item multiple times -- or should we deduplicate?
  /// @param project must be !=null, but marked nullable for backward compatibility -- please, remove all the usages there `project=null`
  /// @return true if all itemsToProcess were processed without conflicts, in one go;
  ///         false, if there were competing concurrent processing for at least 1 item detected
  public final boolean processAll(@NotNull Collection<? extends Item> itemsToProcess,
                                  @Nullable Project project) throws ProcessCanceledException {
    if (processingOnCurrentThread.get() != null) {
      throw new IllegalStateException("Reentrant processAll() call on the same UpdateTask instance");
    }

    processingOnCurrentThread.set(Boolean.TRUE);
    try {
      return processAllNonReentrant(itemsToProcess, project);
    }
    finally {
      processingOnCurrentThread.remove();
    }
  }

  protected abstract void doProcess(Item item, @Nullable Project project);

  

  /// Runs item processing after the public entry point has established the per-thread reentrancy guard
  private boolean processAllNonReentrant(@NotNull Collection<? extends Item> itemsToProcess,
                                         @Nullable Project project) throws ProcessCanceledException {
    boolean allItemsProcessedWithoutConflicts = true;

    while (true) {
      List<Item> itemsToRetryDueToConcurrency = null;
      for (Item item : itemsToProcess) {
        inProgressOperations.down();// read as 'inProgressOperations++'
        try {
          boolean processed = processSerializable(item, project);

          if (!processed) {
            allItemsProcessedWithoutConflicts = false;

            if (itemsToRetryDueToConcurrency == null) {
              itemsToRetryDueToConcurrency = new ArrayList<>(4);
            }
            itemsToRetryDueToConcurrency.add(item);
          }
        }
        finally {
          inProgressOperations.up();// read as 'inProgressOperations--'
        }
        ProgressManager.checkCanceled();
      }

      if (itemsToRetryDueToConcurrency == null) {
        return allItemsProcessedWithoutConflicts;
      }

      itemsToProcess = itemsToRetryDueToConcurrency;
      //we could just loop immediately, but it will be a busy-spin-like looping, too harsh for The Planet
      // => wait _at least for current operations_ to complete => much higher chance to succeed with next attempt:
      while (!inProgressOperations.waitFor(100)) {//read as 'wait for inProgressOperations == 0':
        ProgressManager.checkCanceled();
      }
    }
  }

  /// Process ([#doProcess(Item, Project)]) the item only if the same item is not currently processing by some other thread.
  /// @return true if the item has been processed by the current thread, false if the processing was skipped because the same item
  ///         was processed by some other thread
  private boolean processSerializable(Item item, @Nullable Project project) {
    if (itemsBeingProcessed.add(item)) {
      try {
        //TODO RC: there is a conflict around 'project' arg nullability: project must not be null in doProcess(), but .processAll()
        //         _is_ called with project=null argument sometimes!
        doProcess(item, project);
        return true;
      }
      finally {
        itemsBeingProcessed.remove(item);
      }
    }
    return false;
  }
}
