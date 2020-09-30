// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JobDescriptor {
  @NotNull
  private final @Nls String myDisplayName;
  private int myTotalAmount;
  private int myDoneAmount;
  public static final JobDescriptor[] EMPTY_ARRAY = new JobDescriptor[0];

  public JobDescriptor(@NotNull @Nls String displayName) {
    myDisplayName = displayName;
  }

  @NotNull
  public @Nls String getDisplayName() {
    return myDisplayName;
  }

  public int getTotalAmount() {
    return myTotalAmount;
  }

  public void setTotalAmount(int totalAmount) {
    myTotalAmount = totalAmount;
    myDoneAmount = 0;
  }

  public int getDoneAmount() {
    return myDoneAmount;
  }

  public void setDoneAmount(int doneAmount) {
    myDoneAmount = doneAmount;
  }

  public float getProgress() {
    float localProgress;
    if (getTotalAmount() == 0) {
      localProgress = 0;
    }
    else {
      localProgress = 1.0f * getDoneAmount() / getTotalAmount();
    }

    return localProgress;
  }
}
