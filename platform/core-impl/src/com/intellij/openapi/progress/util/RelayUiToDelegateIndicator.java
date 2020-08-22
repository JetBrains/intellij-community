// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates UI-related methods {@link #setText(String)}, {@link #setText2(String)}, {@link #setFraction(double)} and {@link #setIndeterminate(boolean)}
 * to the specified {@code delegate}.
 * Can be used when want to run process with some temp indicator but display all its changes in the other (global visible) indicator.
 */
public class RelayUiToDelegateIndicator extends AbstractProgressIndicatorExBase {
  private @NotNull final ProgressIndicator myDelegate;

  public RelayUiToDelegateIndicator(@NotNull ProgressIndicator delegate) {
    super(true);
    myDelegate = delegate;
  }

  @Override
  public final void setText(String text) {
    myDelegate.setText(text);
  }

  @Override
  public final void setText2(String text) {
    myDelegate.setText2(text);
  }

  @Override
  public final void setFraction(double fraction) {
    myDelegate.setFraction(fraction);
  }

  @Override
  public final void setIndeterminate(boolean indeterminate) {
    myDelegate.setIndeterminate(indeterminate);
  }

  @Override
  public final void pushState() {
  }

  @Override
  public final void popState() {
  }

  @Override
  protected final void onProgressChange() {
    throw new IllegalStateException("Adding delegates to this indicator is not supported");
  }
}
