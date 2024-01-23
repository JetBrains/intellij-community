// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

public class DumbProgressIndicator implements StandardProgressIndicator {

  @Obsolete
  public static final DumbProgressIndicator INSTANCE = new DumbProgressIndicator();

  @Obsolete
  public DumbProgressIndicator() {
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public final void cancel() {
  }

  @Override
  public final boolean isCanceled() {
    return false;
  }

  @Override
  public final void checkCanceled() {
  }

  @Override
  public void setText(String text) {
  }

  @Override
  public String getText() {
    return null;
  }

  @Override
  public void setText2(String text) {
  }

  @Override
  public String getText2() {
    return null;
  }

  @Override
  public double getFraction() {
    return 0;
  }

  @Override
  public void setFraction(double fraction) {
  }

  @Override
  public void pushState() {
  }

  @Override
  public void popState() {
  }

  @Override
  public boolean isModal() {
    return false;
  }

  @Override
  public @NotNull ModalityState getModalityState() {
    return ModalityState.nonModal();
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
  }

  @Override
  public boolean isIndeterminate() {
    return false;
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
  }

  @Override
  public boolean isPopupWasShown() {
    return false;
  }

  @Override
  public boolean isShowing() {
    return false;
  }
}
