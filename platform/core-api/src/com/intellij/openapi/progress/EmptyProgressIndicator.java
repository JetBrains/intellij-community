/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyProgressIndicator implements StandardProgressIndicator {
  @NotNull
  private final ModalityState myModalityState;

  @NotNull
  private volatile RunState myRunState = RunState.VIRGIN;
  private enum RunState {
    VIRGIN, STARTED, STOPPED
  }
  private volatile boolean myIsCanceled;
  private volatile int myNonCancelableSectionCount;

  public EmptyProgressIndicator() {
    this(ModalityState.defaultModalityState());
  }

  public EmptyProgressIndicator(@NotNull ModalityState modalityState) {
    myModalityState = modalityState;
  }

  @Override
  public void start() {
    if (myRunState == RunState.STARTED) {
      throw new IllegalStateException("Indicator already started");
    }
    myRunState = RunState.STARTED;
    myIsCanceled = false;
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
  public final void cancel() {
    myIsCanceled = true;
    ProgressManager.canceled(this);
  }

  @Override
  public final boolean isCanceled() {
    return myIsCanceled;
  }

  @Override
  public final void checkCanceled() {
    if (myIsCanceled && myNonCancelableSectionCount == 0) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public void setText(String text) {
  }

  @Override
  public String getText() {
    return "";
  }

  @Override
  public void setText2(String text) {
  }

  @Override
  public String getText2() {
    return "";
  }

  @Override
  public double getFraction() {
    return 1;
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
  public void startNonCancelableSection() {
    DeprecatedMethodException.report("Use ProgressManager#executeNonCancelableSection() instead");
    myNonCancelableSectionCount++;
  }

  @Override
  public void finishNonCancelableSection() {
    myNonCancelableSectionCount--;
  }

  @Override
  public boolean isModal() {
    return false;
  }

  @Override
  @NotNull
  public ModalityState getModalityState() {
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

  @NotNull
  public static ProgressIndicator notNullize(@Nullable ProgressIndicator indicator) {
    if (indicator != null) {
      return indicator;
    }
    return new EmptyProgressIndicator();
  }
}
