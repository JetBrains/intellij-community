// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class FilteringProgressView extends BorderLayoutPanel {
  private final JProgressBar myProgressBar = new JProgressBar();
  private final BorderLayoutPanel myProgressPanel = new BorderLayoutPanel();
  private final JBLabel myStopButton = new JBLabel(StartupUiUtil.isUnderDarcula()
                                                   ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close);
  private final JBLabel myProgressText = new JBLabel();

  FilteringProgressView() {
    myStopButton.setOpaque(true);
    myProgressPanel.addToCenter(myProgressBar);
    myProgressPanel.addToRight(myStopButton);
    addToTop(myProgressText);
    addToCenter(myProgressPanel);
  }

  ProgressIndicator getProgressIndicator() {
    return new InstancesProgressIndicator();
  }

  void addStopActionListener(@NotNull Runnable listener) {
    myStopButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listener.run();
      }
    });
  }

  class InstancesProgressIndicator extends ProgressIndicatorBase {
    @Override
    public void start() {
      super.start();
      myProgressBar.setMinimum(0);
      myProgressBar.setMaximum(100);
      myProgressBar.setValue(0);
      myProgressPanel.setVisible(true);
    }

    @Override
    public void stop() {
      super.stop();
      myProgressPanel.setVisible(false);
    }

    @Override
    public void setText(String text) {
      super.setText(text);
      myProgressText.setText(text);
    }

    @Override
    public void setFraction(double fraction) {
      super.setFraction(fraction);
      myProgressBar.setMinimum(0);
      myProgressBar.setMaximum(100);
      myProgressBar.setValue((int)(fraction * 100));
    }
  }
}
