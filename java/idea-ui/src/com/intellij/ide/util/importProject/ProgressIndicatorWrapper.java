// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class ProgressIndicatorWrapper {
  private final @Nullable ProgressIndicator myIndicator;

  public ProgressIndicatorWrapper(@Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public boolean isCanceled() {
    return myIndicator != null && myIndicator.isCanceled();
  }

  public void setText(final @NlsContexts.ProgressText String text) {
    if (myIndicator != null) {
      myIndicator.setText(text);
    }
  }

  public void setText2(final @NlsContexts.ProgressDetails String text) {
    if (myIndicator != null) {
      myIndicator.setText2(text);
    }
  }

  public void setFraction(final double fraction) {
    if (myIndicator != null) {
      myIndicator.setFraction(fraction);
    }
  }

  public void pushState() {
    if (myIndicator != null) {
      myIndicator.pushState();
    }
  }

  public void popState() {
    if (myIndicator != null) {
      myIndicator.popState();
    }
  }

  public void setIndeterminate(final boolean indeterminate) {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(indeterminate);
    }
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }
}
