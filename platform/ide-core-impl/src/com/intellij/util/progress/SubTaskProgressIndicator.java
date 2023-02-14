// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress;

import com.intellij.concurrency.SensitiveProgressWrapper;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public final class SubTaskProgressIndicator extends SensitiveProgressWrapper {
  private final ConcurrentTasksProgressManager myProgressManager;
  private final AtomicLong myFraction;
  private final int myTaskWeight;

  SubTaskProgressIndicator(ConcurrentTasksProgressManager progressManager, int taskWeight) {
    super(progressManager.getParent());
    myProgressManager = progressManager;
    myTaskWeight = taskWeight;
    myFraction = new AtomicLong();
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    //Do nothing because "isIndeterminate" is controlled by original indicator of the [progressManager].
  }

  @Override
  public void setText(String text) {
    //The main text of the sub-task indicator becomes the text2 of the parent indicator.
    myProgressManager.setText2(this, text);
  }

  @Override
  public void setFraction(double newValue) {
    double oldValue = Double.longBitsToDouble(myFraction.getAndSet(Double.doubleToRawLongBits(newValue)));
    myProgressManager.updateTaskFraction(newValue - oldValue, myTaskWeight);
  }

  @Override
  public void setText2(String text) {
    //Ignore the text2
  }

  @Override
  public double getFraction() {
    return Double.longBitsToDouble(myFraction.get());
  }

  public void finished() {
    setFraction(1);
    myProgressManager.setText2(this, null);
  }
}