// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import org.jetbrains.annotations.NotNull;

final class NonCancelableIndicator implements StandardProgressIndicator {

  static final NonCancelableIndicator INSTANCE = new NonCancelableIndicator();

  private NonCancelableIndicator() {
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public void checkCanceled() {
    ((CoreProgressManager)ProgressManager.getInstance()).runCheckCanceledHooks(this);
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
  public void cancel() {

  }

  @Override
  public boolean isCanceled() {
    return false;
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

  @Override
  public String toString() {
    return "NonCancelableIndicator.INSTANCE: "+super.toString();
  }
}
