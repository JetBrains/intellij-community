// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyProgressIndicator extends EmptyProgressIndicatorBase implements StandardProgressIndicator {
  private volatile boolean myIsCanceled;

  @Obsolete
  public EmptyProgressIndicator() { }

  @Obsolete
  public EmptyProgressIndicator(@NotNull ModalityState modalityState) {
    super(modalityState);
  }

  @Override
  public void start() {
    super.start();
    myIsCanceled = false;
  }

  @Override
  public final void cancel() {
    myIsCanceled = true;
    ProgressManager.canceled(this);
  }

  @Override
  public final boolean isCanceled() {
    return myIsCanceled;
  }

  /**
   * @deprecated instead of using this function somewhere higher in the stacktrace,
   * make sure the indicator is installed by whoever calls the function which calls {@code notNullize},
   * or, in other words, make sure {@link ProgressManager#getGlobalProgressIndicator()} returns non-null value.
   * This function is dangerous because it makes the code effectively non-cancellable suppressing any assertions.
   */
  @Deprecated
  public static @NotNull ProgressIndicator notNullize(@Nullable ProgressIndicator indicator) {
    return indicator != null ? indicator : new EmptyProgressIndicator();
  }
}
