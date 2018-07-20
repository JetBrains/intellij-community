/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

public interface ProgressIndicator {
  void start();

  void stop();

  boolean isRunning();

  void cancel();

  boolean isCanceled();

  /**
   * Sets text above the progress bar
   * @param text Text to set
   * @see #setText2(String)
   */
  void setText(String text);

  String getText();

  /**
   * Sets text under the progress bar
   * @param text Text to set
   * @see #setText(String)
   */
  void setText2(String text);

  String getText2();

  double getFraction();

  void setFraction(double fraction);

  void pushState();

  void popState();

  /** use {@link ProgressManager#executeNonCancelableSection(Runnable)} instead */
  @Deprecated
  default void startNonCancelableSection() {}

  /** use {@link ProgressManager#executeNonCancelableSection(Runnable)} instead */
  @Deprecated
  default void finishNonCancelableSection() {}

  boolean isModal();

  @NotNull
  ModalityState getModalityState();

  void setModalityProgress(ProgressIndicator modalityProgress);

  boolean isIndeterminate();

  void setIndeterminate(boolean indeterminate);

  void checkCanceled() throws ProcessCanceledException;

  boolean isPopupWasShown();
  boolean isShowing();
}
