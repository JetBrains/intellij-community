// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class DelegatingProgressIndicator implements WrappedProgressIndicator, StandardProgressIndicator {
  private final ProgressIndicator myIndicator;

  public DelegatingProgressIndicator(@NotNull ProgressIndicator indicator) {
    myIndicator = indicator;
    ProgressManager.assertNotCircular(indicator);
  }

  public DelegatingProgressIndicator() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      myIndicator = new EmptyProgressIndicator();
    }
    else {
      myIndicator = indicator;
      ProgressManager.assertNotCircular(indicator);
    }
  }

  @Override
  public void start() {
    myIndicator.start();
  }

  @Override
  public void stop() {
    myIndicator.stop();
  }

  @Override
  public boolean isRunning() {
    return myIndicator.isRunning();
  }

  @Override
  public final void cancel() {
    myIndicator.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return myIndicator.isCanceled();
  }

  @Override
  public void setText(@Nls @NlsContexts.ProgressText String text) {
    myIndicator.setText(text);
  }

  @Override
  public String getText() {
    return myIndicator.getText();
  }

  @Override
  public void setText2(@Nls @NlsContexts.ProgressDetails String text) {
    myIndicator.setText2(text);
  }

  @Override
  public String getText2() {
    return myIndicator.getText2();
  }

  @Override
  public double getFraction() {
    return myIndicator.getFraction();
  }

  @Override
  public void setFraction(final double fraction) {
    myIndicator.setFraction(fraction);
  }

  @Override
  public void pushState() {
    myIndicator.pushState();
  }

  @Override
  public void popState() {
    myIndicator.popState();
  }

  @Override
  public void startNonCancelableSection() {
    myIndicator.startNonCancelableSection();
  }

  @Override
  public void finishNonCancelableSection() {
    myIndicator.finishNonCancelableSection();
  }

  @Override
  public boolean isModal() {
    return myIndicator.isModal();
  }

  @Override
  @NotNull
  public ModalityState getModalityState() {
    return myIndicator.getModalityState();
  }

  @Override
  public void setModalityProgress(final ProgressIndicator modalityProgress) {
    myIndicator.setModalityProgress(modalityProgress);
  }

  @Override
  public boolean isIndeterminate() {
    return myIndicator.isIndeterminate();
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    myIndicator.setIndeterminate(indeterminate);
  }

  @Override
  public final void checkCanceled() throws ProcessCanceledException {
    myIndicator.checkCanceled();
  }

  protected final ProgressIndicator getDelegate() {
    return myIndicator;
  }

  @NotNull
  @Override
  public ProgressIndicator getOriginalProgressIndicator() {
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
