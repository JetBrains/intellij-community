// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.Nullable;


public abstract class ProgressIndicatorProvider {
  public static ProgressIndicatorProvider getInstance() {
    return ProgressManager.getInstance();
  }

  public abstract ProgressIndicator getProgressIndicator();

  protected abstract void doCheckCanceled() throws ProcessCanceledException;

  @Nullable
  public static ProgressIndicator getGlobalProgressIndicator() {
    ProgressManager instance = ProgressManager.ourInstance;
    return instance == null ? null : instance.getProgressIndicator();
  }

  public static void checkCanceled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
  }
}
