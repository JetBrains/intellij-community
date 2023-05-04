// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Propagation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Describes a task for {@link MergingUpdateQueue}. Equal tasks (instances with the equal {@code identity} objects) are merged, i.e.,
 * only the first of them is executed. If some tasks are more generic than others override {@link #canEat(Update)} method.
 *
 * @see MergingUpdateQueue
 */
public abstract class Update extends ComparableObject.Impl implements Runnable {
  public static final int LOW_PRIORITY = 999;
  public static final int HIGH_PRIORITY = 10;

  private volatile boolean myProcessed;
  private volatile boolean myRejected;
  private final @Nullable CoroutineContext myContext;
  private final boolean myExecuteInWriteAction;

  private final int myPriority;

  public Update(@NonNls Object identity) {
    this(identity, false);
  }

  public Update(@NonNls Object identity, int priority) {
    this(identity, false, priority);
  }

  public Update(@NonNls Object identity, boolean executeInWriteAction) {
    this(identity, executeInWriteAction, LOW_PRIORITY);
  }

  public Update(@NonNls Object identity, boolean executeInWriteAction, int priority) {
    this(identity, executeInWriteAction, priority,
         AppExecutorUtil.propagateContextOrCancellation() ? ThreadContext.currentThreadContext().minusKey(Job.Key) : null);
  }

  private Update(@NonNls Object identity, boolean executeInWriteAction, int priority, @Nullable CoroutineContext context) {
    super(context == null ? new Object[]{identity} : new Object[]{identity, ThreadContext.getContextSkeleton(context)});
    myContext = context;
    myExecuteInWriteAction = executeInWriteAction;
    myPriority = priority;
  }

  public boolean isDisposed() {
    return false;
  }
  
  public boolean isExpired() {
    return false;
  }

  public boolean wasProcessed() {
    return myProcessed;
  }

  public void setProcessed() {
    myProcessed = true;
  }

  public boolean executeInWriteAction() {
    return myExecuteInWriteAction;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return super.toString() + " Objects: " + Arrays.asList(getEqualityObjects());
  }

  public final int getPriority() {
    return myPriority;
  }

  final boolean actuallyCanEat(Update update) {
    if (myContext != null) {
      return update.myContext != null &&
             ThreadContext.getContextSkeleton(myContext).equals(ThreadContext.getContextSkeleton(update.myContext)) &&
             canEat(update);
    } else {
      return update.myContext == null &&
             canEat(update);
    }
  }

  final void runUpdate() {
    if (myContext == null) {
      run();
    } else try (AccessToken ignored = ThreadContext.installThreadContext(myContext, true)) {
      run();
    }
  }

  /**
   * Override this method and return {@code true} if this task is more generic than the passed {@code update}.
   * For example, if this task repaints the whole frame and the passed task repaints some component on the frame,
   * the less generic tasks will be removed from the queue before execution.
   */
  public boolean canEat(@NotNull Update update) {
    return false;
  }

  public void setRejected() {
    myRejected = true;
  }

  public boolean isRejected() {
    return myRejected;
  }

  @NotNull
  public static Update create(@NonNls Object identity, @NotNull Runnable runnable) {
    return new Update(identity) {
      @Override
      public void run() {
        runnable.run();
      }
    };
  }
}
