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

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 12, 2007
 */
public class DelegatingProgressIndicator implements ProgressIndicator {

  private static final EmptyProgressIndicator EMPTY_INDICATOR = new EmptyProgressIndicator();
  private final ProgressIndicator myIndicator;

  public DelegatingProgressIndicator() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    myIndicator = indicator == null ? EMPTY_INDICATOR : indicator;
  }

  public void start() {
    getDelegate().start();
  }

  public void stop() {
    getDelegate().stop();
  }

  public boolean isRunning() {
    return getDelegate().isRunning();
  }

  public void cancel() {
    getDelegate().cancel();
  }

  public boolean isCanceled() {
    return getDelegate().isCanceled();
  }

  public void setText(final String text) {
    getDelegate().setText(text);
  }

  public String getText() {
    return getDelegate().getText();
  }

  public void setText2(final String text) {
    getDelegate().setText2(text);
  }

  public String getText2() {
    return getDelegate().getText2();
  }

  public double getFraction() {
    return getDelegate().getFraction();
  }

  public void setFraction(final double fraction) {
    getDelegate().setFraction(fraction);
  }

  public void pushState() {
    getDelegate().pushState();
  }

  public void popState() {
    getDelegate().popState();
  }

  public void startNonCancelableSection() {
    getDelegate().startNonCancelableSection();
  }

  public void finishNonCancelableSection() {
    getDelegate().finishNonCancelableSection();
  }

  public boolean isModal() {
    return getDelegate().isModal();
  }

  public ModalityState getModalityState() {
    return getDelegate().getModalityState();
  }

  public void setModalityProgress(final ProgressIndicator modalityProgress) {
    getDelegate().setModalityProgress(modalityProgress);
  }

  public boolean isIndeterminate() {
    return getDelegate().isIndeterminate();
  }

  public void setIndeterminate(final boolean indeterminate) {
    getDelegate().setIndeterminate(indeterminate);
  }

  public void checkCanceled() throws ProcessCanceledException {
    getDelegate().checkCanceled();
  }

  private ProgressIndicator getDelegate() {
    return myIndicator;
  }
}
