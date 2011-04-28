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

package com.intellij.codeInspection.ex;

/**
 * @author max
 */
public class JobDescriptor {
  private final String myDisplayName;
  private int myTotalAmount;
  private int myDoneAmount;
  public static final JobDescriptor[] EMPTY_ARRAY = new JobDescriptor[0];

  public JobDescriptor(String displayName) {
    myDisplayName = displayName;
  }

  public String getDisplayName() {
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
    if (doneAmount > getTotalAmount()) {
      int i = 0;
    }
    if (doneAmount < getDoneAmount()) {
      int i = 0;
    }
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
