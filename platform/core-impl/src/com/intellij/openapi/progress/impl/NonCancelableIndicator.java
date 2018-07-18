/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.NonCancelableSection;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import org.jetbrains.annotations.NotNull;

class NonCancelableIndicator implements NonCancelableSection, StandardProgressIndicator {
  static final NonCancelableIndicator INSTANCE = new NonCancelableIndicator() {
    @Override
    public int hashCode() {
      return 0;
    }
  };

  protected NonCancelableIndicator() {
  }

  @Override
  public void done() {
    ProgressIndicator currentIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (currentIndicator != this) {
      throw new AssertionError("Trying do .done() NonCancelableSection, which is already done");
    }
  }

  @Override
  public final void checkCanceled() {
    CoreProgressManager.runCheckCanceledHooks(this);
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

  @NotNull
  @Override
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
