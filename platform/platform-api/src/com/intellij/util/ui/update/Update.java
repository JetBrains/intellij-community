/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.update;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Describes a task for {@link MergingUpdateQueue}. Equal tasks (instances with the equal {@code identity} objects) are merged, i.e.
 * only the first of them is executed. If some tasks are more generic than others override {@link #canEat(Update)} method.
 *
 * @see MergingUpdateQueue
 */
public abstract class Update extends ComparableObject.Impl implements Runnable {

  public static final int LOW_PRIORITY = 999;
  public static final int HIGH_PRIORITY = 10;

  private boolean myProcessed;
  private boolean myRejected;
  private final boolean myExecuteInWriteAction;

  private int myPriority = LOW_PRIORITY;

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
    super(identity);
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

  /**
   * Override this method and return {@code true} if this task is more generic than the passed {@code update}, e.g. this tasks repaint the
   * whole frame and the passed task repaint some component on the frame. In that case the less generic tasks will be removed from the queue
   * before execution.
   */
  public boolean canEat(Update update) {
    return false;
  }

  public void setRejected() {
    myRejected = true;
  }

  public boolean isRejected() {
    return myRejected;
  }

  public static Update create(@NonNls Object identity, @NotNull Runnable runnable) {
    return new Update(identity) {
      @Override
      public void run() {
        runnable.run();
      }
    };
  }
}
