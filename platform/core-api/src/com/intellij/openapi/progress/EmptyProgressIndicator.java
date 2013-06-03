/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

public class EmptyProgressIndicator implements ProgressIndicator {
  private volatile boolean myIsRunning = false;
  private volatile boolean myIsCanceled = false;

  @Override
  public void start() {
    myIsRunning = true;
    myIsCanceled = false;
  }

  @Override
  public void stop() {
    myIsRunning = false;
  }

  @Override
  public boolean isRunning() {
    return myIsRunning;
  }

  @Override
  public void cancel() {
    myIsCanceled = true;
  }

  @Override
  public boolean isCanceled() {
    return myIsCanceled;
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
  }

  @Override
  public void finishNonCancelableSection() {
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
  public void checkCanceled() {
    if (myIsCanceled) {
      throw new ProcessCanceledException();
    }
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
