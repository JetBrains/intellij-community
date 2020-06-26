// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.util.SimpleModificationTracker;

public final class ActivityTracker extends SimpleModificationTracker {
  private static final ActivityTracker INSTANCE = new ActivityTracker();

  public static ActivityTracker getInstance() {
    return INSTANCE;
  }

  private ActivityTracker() {
  }

  public int getCount() {
    return (int)getModificationCount();
  }

  public void inc() {
    incModificationCount();
  }
}