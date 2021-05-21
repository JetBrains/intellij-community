// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public final class ConcurrentTasksProgressManager {
  private final ProgressIndicator myParent;
  private final int myTotalWeight;
  private final AtomicLong myTotalFraction;
  private final Object myLock = new Object();
  private final LinkedHashMap<SubTaskProgressIndicator, @NlsContexts.ProgressDetails String> myText2Stack = new LinkedHashMap<>();
  private final AtomicInteger myRemainingTotalWeight;

  public ConcurrentTasksProgressManager(ProgressIndicator parent, int totalWeight) {
    if (totalWeight < 0) {
      throw new IllegalArgumentException("Total weight must not be negative: " + totalWeight);
    }
    myParent = parent;
    myTotalWeight = totalWeight;
    myTotalFraction = new AtomicLong();
    myRemainingTotalWeight = new AtomicInteger(totalWeight);
  }

  @NotNull
  public SubTaskProgressIndicator createSubTaskIndicator(int taskWeight) {
    if (taskWeight < 0) {
      throw new IllegalArgumentException("Task weight must not be negative: " + taskWeight);
    }
    if (myRemainingTotalWeight.addAndGet(-taskWeight) < 0) {
      throw new IllegalStateException("Attempted to create more task indicators than registered in constructor");
    }
    return new SubTaskProgressIndicator(this, taskWeight);
  }

  @NotNull
  ProgressIndicator getParent() {
    return myParent;
  }

  void updateTaskFraction(double taskDeltaFraction, int taskWeight) {
    double delta = myTotalWeight == 0 ? 0 : taskDeltaFraction * taskWeight / myTotalWeight;
    while (true) {
      long current = myTotalFraction.get();
      double currentVal = Double.longBitsToDouble(current);
      double nextVal = currentVal + delta;
      long next = Double.doubleToRawLongBits(nextVal);
      if (myTotalFraction.compareAndSet(current, next)) {
        myParent.setFraction(nextVal);
        break;
      }
    }
  }

  public void setText(@NotNull @NlsContexts.ProgressText String text) {
    myParent.setText(text);
  }

  void setText2(@NotNull SubTaskProgressIndicator subTask, @Nullable @NlsContexts.ProgressDetails String text) {
    if (text != null) {
      synchronized (myLock) {
        myText2Stack.put(subTask, text);
      }
      myParent.setText2(text);
    }
    else {
      String prev;
      synchronized (myLock) {
        myText2Stack.remove(subTask);
        prev = myText2Stack.getLastValue();
      }
      if (prev != null) {
        myParent.setText2(prev);
      }
    }
  }
}
