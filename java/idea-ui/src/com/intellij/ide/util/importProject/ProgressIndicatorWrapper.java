/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.importProject;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2007
 */
public class ProgressIndicatorWrapper {
  @Nullable
  private final ProgressIndicator myIndicator;

  public ProgressIndicatorWrapper(@Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public boolean isCanceled() {
    return myIndicator != null && myIndicator.isCanceled();
  }

  public void setText(final String text) {
    if (myIndicator != null) {
      myIndicator.setText(text);
    }
  }

  public void setText2(final String text) {
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
