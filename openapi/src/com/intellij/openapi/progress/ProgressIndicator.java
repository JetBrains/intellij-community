/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;

public interface ProgressIndicator {
  void start();

  void stop();

  boolean isRunning();

  void cancel();

  boolean isCanceled();

  void setText(String text);

  String getText();

  void setText2(String text);

  String getText2();

  double getFraction();

  void setFraction(double fraction);

  void pushState();

  void popState();

  void startNonCancelableSection();

  void finishNonCancelableSection();

  boolean isModal();

  ModalityState getModalityState();

  void setModalityProgress(ProgressIndicator modalityProgress);

  boolean isIndeterminate();

  void setIndeterminate(boolean indeterminate);

  void checkCanceled();
}