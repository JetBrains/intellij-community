package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

public class DumbProgressIndicator implements StandardProgressIndicator {
  public static final DumbProgressIndicator INSTANCE = new DumbProgressIndicator();

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
  @NotNull
  public ModalityState getModalityState() {
    return ModalityState.NON_MODAL;
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
