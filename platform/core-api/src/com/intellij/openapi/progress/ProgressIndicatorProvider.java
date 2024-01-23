// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Obsolete;

public abstract class ProgressIndicatorProvider {
  public static ProgressIndicatorProvider getInstance() {
    return ProgressManager.getInstance();
  }

  @Obsolete
  public abstract ProgressIndicator getProgressIndicator();

  protected abstract void doCheckCanceled() throws ProcessCanceledException;

  /**
   * @return progress indicator under which this method is executing (see {@link ProgressManager} on how to run a process under a progress indicator)
   * or null if this code is running outside any progress.
   */
  @Obsolete
  public static ProgressIndicator getGlobalProgressIndicator() {
    ProgressManager instance = ProgressManager.getInstanceOrNull();
    return instance == null ? null : instance.getProgressIndicator();
  }

  public static void checkCanceled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
  }
}
