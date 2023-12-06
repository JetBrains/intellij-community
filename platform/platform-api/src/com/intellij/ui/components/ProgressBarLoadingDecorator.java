// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProgressBarLoadingDecorator extends LoadingDecorator {
  private final AtomicBoolean loadingStarted = new AtomicBoolean(false);
  private JProgressBar myProgressBar;

  public ProgressBarLoadingDecorator(@NotNull JPanel contentPanel, @NotNull Disposable disposable, int startDelayMs) {
    super(contentPanel, disposable, startDelayMs, true);
  }

  @Override
  protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AnimatedIcon icon) {
    parent.setLayout(new BorderLayout());
    NonOpaquePanel result = new NonOpaquePanel();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
    myProgressBar = new JProgressBar();
    myProgressBar.setIndeterminate(true);
    myProgressBar.putClientProperty("ProgressBar.stripeWidth", 2);
    myProgressBar.putClientProperty("ProgressBar.flatEnds", Boolean.TRUE);
    result.add(myProgressBar);
    parent.add(result, isOnTop() ? BorderLayout.NORTH : BorderLayout.SOUTH);
    return result;
  }

  protected boolean isOnTop() {
    return true;
  }

  public @NotNull JProgressBar getProgressBar() {
    return myProgressBar;
  }

  @Override
  public void startLoading(boolean takeSnapshot) {
    if (loadingStarted.compareAndSet(false, true)) {
      super.startLoading(takeSnapshot);
    }
  }

  public void startLoading() {
    startLoading(false);
  }

  @Override
  public void stopLoading() {
    if (loadingStarted.compareAndSet(true, false)) {
      super.stopLoading();
    }
  }
}
