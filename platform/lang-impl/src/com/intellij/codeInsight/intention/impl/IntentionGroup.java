// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

public enum IntentionGroup {
  ERROR(30),
  INSPECTION(20),
  REMOTE_ERROR(10), // problems collected from other offsets in this line
  NOTIFICATION(7),
  GUTTER(-5),
  ADVERTISEMENT(-10),
  EMPTY_ACTION(-10),
  OTHER(0);

  private final int myPriority;

  IntentionGroup(int priority) {
    myPriority = priority;
  }

  public int getPriority() {
    return myPriority;
  }
}
