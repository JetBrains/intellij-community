// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.ProgressBarLoadingDecorator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
final class BuildProgressStripe extends JBPanel {
  private final @NotNull JBPanel myPanel;
  private final NotNullLazyValue<ProgressBarLoadingDecorator> myCreateLoadingDecorator;
  private ProgressBarLoadingDecorator myDecorator;

  BuildProgressStripe(@NotNull JComponent targetComponent, @NotNull Disposable parent, int startDelayMs) {
    super(new BorderLayout());
    myPanel = new JBPanel(new BorderLayout());
    myPanel.setOpaque(false);
    myPanel.add(targetComponent);
    myCreateLoadingDecorator = NotNullLazyValue.lazy(() -> {
      return new ProgressBarLoadingDecorator(myPanel, parent, startDelayMs);
    });
    createLoadingDecorator();
  }

  public void updateProgress(long total, long progress) {
    if (total == progress) {
      stopLoading();
      return;
    }
    boolean isDeterminate = total > 0 && progress > 0;
    JProgressBar progressBar = getProgressBar();
    boolean isProgressBarIndeterminate = progressBar.isIndeterminate();
    if (isDeterminate) {
      startLoading();
      progressBar.setValue(Math.toIntExact(progress * 100 / total));
      if (isProgressBarIndeterminate) {
        progressBar.setIndeterminate(false);
      }
    }
    else if (!isProgressBarIndeterminate) {
      progressBar.setIndeterminate(true);
    }
  }

  void startLoading() {
    myDecorator.startLoading();
  }

  void stopLoading() {
    JProgressBar progressBar = getProgressBar();
    if (!progressBar.isIndeterminate()) {
      progressBar.setValue(100);
    }
    myDecorator.stopLoading();
  }

  private JProgressBar getProgressBar() {
    return myCreateLoadingDecorator.getValue().getProgressBar();
  }

  private void createLoadingDecorator() {
    myDecorator = myCreateLoadingDecorator.getValue();
    add(myDecorator.getComponent(), BorderLayout.CENTER);
    myDecorator.setLoadingText("");
  }
}
