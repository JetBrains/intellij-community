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
package com.intellij.ide.util;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 12, 2007
 */
public class DelegatingProgressIndicator implements ProgressIndicator {
  private final ProgressIndicator myIndicator;

  public DelegatingProgressIndicator(ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public DelegatingProgressIndicator() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    myIndicator = indicator == null ? new EmptyProgressIndicator() : indicator;
  }

  public void start() {
    myIndicator.start();
  }

  public void stop() {
    myIndicator.stop();
  }

  public boolean isRunning() {
    return myIndicator.isRunning();
  }

  public void cancel() {
    myIndicator.cancel();
  }

  public boolean isCanceled() {
    return myIndicator.isCanceled();
  }

  public void setText(final String text) {
    myIndicator.setText(text);
  }

  public String getText() {
    return myIndicator.getText();
  }

  public void setText2(final String text) {
    myIndicator.setText2(text);
  }

  public String getText2() {
    return myIndicator.getText2();
  }

  public double getFraction() {
    return myIndicator.getFraction();
  }

  public void setFraction(final double fraction) {
    myIndicator.setFraction(fraction);
  }

  public void pushState() {
    myIndicator.pushState();
  }

  public void popState() {
    myIndicator.popState();
  }

  public void startNonCancelableSection() {
    myIndicator.startNonCancelableSection();
  }

  public void finishNonCancelableSection() {
    myIndicator.finishNonCancelableSection();
  }

  public boolean isModal() {
    return myIndicator.isModal();
  }

  @NotNull
  public ModalityState getModalityState() {
    return myIndicator.getModalityState();
  }

  public void setModalityProgress(final ProgressIndicator modalityProgress) {
    myIndicator.setModalityProgress(modalityProgress);
  }

  public boolean isIndeterminate() {
    return myIndicator.isIndeterminate();
  }

  public void setIndeterminate(final boolean indeterminate) {
    myIndicator.setIndeterminate(indeterminate);
  }

  public void checkCanceled() throws ProcessCanceledException {
    myIndicator.checkCanceled();
  }

  protected final ProgressIndicator getDelegate() {
    return myIndicator;
  }

  @Override
  public boolean isPopupWasShown() {
    return myIndicator.isPopupWasShown();
  }

  @Override
  public boolean isShowing() {
    return myIndicator.isShowing();
  }
}
