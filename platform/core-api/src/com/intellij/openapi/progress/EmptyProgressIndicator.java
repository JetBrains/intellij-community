// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyProgressIndicator extends EmptyProgressIndicatorBase implements StandardProgressIndicator {
  private volatile boolean myIsCanceled;

  public EmptyProgressIndicator() { }

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

  public static @NotNull ProgressIndicator notNullize(@Nullable ProgressIndicator indicator) {
    return indicator != null ? indicator : new EmptyProgressIndicator();
  }
}
