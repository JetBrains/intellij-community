// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

@ApiStatus.Internal
public abstract class UpdateTask<Type> {
  private static final boolean DEBUG_TO_STDOUT = false;

  private final Semaphore myUpdateSemaphore = new Semaphore();
  private final Set<Type> myItemsBeingIndexed = ConcurrentCollectionFactory.createConcurrentSet();

  /**
   * @param project must be !=null, but marked nullable for backward compatibility -- please, remove all the usages there `project=null`
   * @return true if all itemsToProcess were processed, false, if some items were skipped, because they were processed by parallel
   *         invocation of the same method from another thread
   */
  public final boolean processAll(@NotNull Collection<? extends Type> itemsToProcess,
                                  @Nullable Project project) {
    if (DEBUG_TO_STDOUT) trace("enter processAll");
    try {
      boolean hasMoreToProcess;
      boolean allItemsProcessed = true;

      do {
        hasMoreToProcess = false;
        if (DEBUG_TO_STDOUT) trace("observing " + itemsToProcess.size());
        // todo we can decrease itemsToProcess
        for (Type item : itemsToProcess) {
          myUpdateSemaphore.down();

          try {
            if (DEBUG_TO_STDOUT) trace("about to process");
            boolean processed = process(item, project);
            if (DEBUG_TO_STDOUT) trace(processed ? "processed " : "skipped");

            if (!processed) {
              hasMoreToProcess = true;
              allItemsProcessed = false;
            }
          }
          finally {
            myUpdateSemaphore.up();
          }
          ProgressManager.checkCanceled();
        }

        do {
          ProgressManager.checkCanceled();
        }
        while (!myUpdateSemaphore.waitFor(100));
        if (DEBUG_TO_STDOUT) if (hasMoreToProcess) trace("reiterating");
      }
      while (hasMoreToProcess);

      return allItemsProcessed;
    } finally {
      if (DEBUG_TO_STDOUT) trace("exits processAll");
    }
  }

  private boolean process(Type item,
                          @Nullable Project project) {
    if (myItemsBeingIndexed.add(item)) {
      try {
        doProcess(item, project);
        return true;
      } finally {
        myItemsBeingIndexed.remove(item);
      }
    }
    return false;
  }

  @ApiStatus.Internal
  protected abstract void doProcess(Type item, @Nullable Project project);

  protected static void trace(String s) {
    System.out.println(Thread.currentThread() + " " + s);
  }
}