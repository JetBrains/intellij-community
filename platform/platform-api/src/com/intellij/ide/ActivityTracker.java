// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.SimpleModificationTracker;

/**
 * See {@link com.intellij.openapi.actionSystem.AnAction#update(AnActionEvent)} javadoc.
 */
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