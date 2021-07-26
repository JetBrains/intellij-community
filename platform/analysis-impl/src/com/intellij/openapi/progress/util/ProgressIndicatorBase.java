// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.StandardProgressIndicator;

public class ProgressIndicatorBase extends AbstractProgressIndicatorExBase implements StandardProgressIndicator {
  /**
   * This constant defines default delay for showing progress dialog (in millis).
   *
   * @see #setDelayInMillis(int)
   */
  public static final int DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS = 300;
  int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

  public ProgressIndicatorBase() {
    super();
  }

  public ProgressIndicatorBase(boolean reusable) {
    super(reusable);
  }

  public ProgressIndicatorBase(boolean reusable, boolean allowSystemActivity) {
    super(reusable);
    if (!allowSystemActivity) dontStartActivity();
  }

  @Override
  public final void cancel() {
    super.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return super.isCanceled();
  }

  /**
   * There is a possible case that many short (in terms of time) progress tasks are executed in a small amount of time.
   * Problem: UI blinks and looks ugly if we show progress dialog for every such task (every dialog disappears shortly).
   * Solution is to postpone showing progress dialog in assumption that the task may be already finished when it's
   * time to show the dialog.
   * <p/>
   * Default value is {@link #DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS}
   *
   * @param delayInMillis   new delay time in milliseconds
   */
  public void setDelayInMillis(int delayInMillis) {
    myDelayInMillis = delayInMillis;
  }
}
