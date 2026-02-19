// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

public interface ProgressIndicatorWithDelayedPresentation {
  /**
   * This constant defines default delay for showing progress dialog (in millis).
   *
   * @see #setDelayInMillis(int)
   * @deprecated deprecated in favor of {@link com.intellij.ui.progress.ProgressUIUtil#DEFAULT_PROGRESS_DELAY_MILLIS} to avoid the dependency on analysis.impl module
   */
  @Deprecated
  int DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS = 300;

  /**
   * There is a possible case that many short (in terms of time) progress tasks are executed in a small amount of time.
   * Problem: UI blinks and looks ugly if we show progress dialog for every such task (every dialog disappears shortly).
   * Solution is to postpone showing progress dialog in assumption that the task may be already finished when it's
   * time to show the dialog.
   *
   * @param delayInMillis   new delay time in milliseconds
   */
  void setDelayInMillis(int delayInMillis);
}
