// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

public abstract class EmptyProgressIndicatorBase implements ProgressIndicator {
  private final @NotNull ModalityState myModalityState;
  private volatile @NotNull RunState myRunState = RunState.VIRGIN;

  private enum RunState {
    VIRGIN, STARTED, STOPPED
  }

  private volatile int myNonCancelableSectionCount;

  @Obsolete
  public EmptyProgressIndicatorBase() {
    this(ModalityState.defaultModalityState());
  }

  @Obsolete
  public EmptyProgressIndicatorBase(@NotNull ModalityState modalityState) {
    myModalityState = modalityState;
  }

  @Override
  public void start() {
    if (myRunState == RunState.STARTED) {
      throw new IllegalStateException("Indicator already started");
    }
    myRunState = RunState.STARTED;
  }

  @Override
  public void stop() {
    switch (myRunState) {
      case VIRGIN:
        throw new IllegalStateException("Indicator can't be stopped because it wasn't started");
      case STARTED:
        myRunState = RunState.STOPPED;
        break;
      case STOPPED:
        throw new IllegalStateException("Indicator already stopped");
    }
  }

  @Override
  public boolean isRunning() {
    return myRunState == RunState.STARTED;
  }

  @Override
  public final void checkCanceled() {
    if (isCanceled() && myNonCancelableSectionCount == 0 && !Cancellation.isInNonCancelableSection()) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public void setText(String text) { }

  @Override
  public String getText() {
    return "";
  }

  @Override
  public void setText2(String text) { }

  @Override
  public String getText2() {
    return "";
  }

  @Override
  public double getFraction() {
    return 1;
  }

  @Override
  public void setFraction(double fraction) { }

  @Override
  public void pushState() { }

  @Override
  public void popState() { }

  @Override
  @SuppressWarnings({"deprecation", "NonAtomicOperationOnVolatileField"})
  public void startNonCancelableSection() {
    PluginException.reportDeprecatedUsage("ProgressIndicator#startNonCancelableSection", "Use `ProgressManager.executeNonCancelableSection()` instead");
    myNonCancelableSectionCount++;
  }

  @Override
  @SuppressWarnings({"deprecation", "NonAtomicOperationOnVolatileField"})
  public void finishNonCancelableSection() {
    myNonCancelableSectionCount--;
  }

  @Override
  public boolean isModal() {
    return false;
  }

  @Override
  public @NotNull ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
    if (isRunning()) {
      throw new IllegalStateException("Can't change modality progress for already running indicator");
    }
  }

  @Override
  public boolean isIndeterminate() {
    return false;
  }

  @Override
  public void setIndeterminate(boolean indeterminate) { }

  @Override
  public boolean isPopupWasShown() {
    return false;
  }

  @Override
  public boolean isShowing() {
    return false;
  }
}
