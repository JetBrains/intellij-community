// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.memory.filtering.FilteringResult;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class FilteringProgressView extends BorderLayoutPanel {
  private final JProgressBar myProgressBar = new JProgressBar();
  private final BorderLayoutPanel myProgressPanel = new BorderLayoutPanel();
  private final JBLabel myStopButton = new JBLabel(StartupUiUtil.isUnderDarcula()
                                                   ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close);
  private final JBLabel myProgressText = new JBLabel();

  private int myProceedCount = 0;
  private int myErrorCount = 0;
  private int myMatchedCount = 0;

  private boolean myIsInProcess = false;

  @Nullable
  private FilteringResult myCompletionReason = null;

  FilteringProgressView() {
    myStopButton.setOpaque(true);
    myProgressPanel.addToCenter(myProgressBar);
    myProgressPanel.addToRight(myStopButton);
    addToTop(myProgressText);
    addToCenter(myProgressPanel);
  }

  void addStopActionListener(@NotNull Runnable listener) {
    myStopButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listener.run();
      }
    });
  }

  void updateProgress(int proceedCount, int matchedCount, int errorCount) {
    if (myIsInProcess) {
      myProceedCount = proceedCount;
      myMatchedCount = matchedCount;
      myErrorCount = errorCount;
      myProgressBar.setValue(myProceedCount);
      myProgressText.setText(getDescription());
    }
  }

  void start(int totalCount) {
    reset();
    myIsInProcess = true;
    myProgressBar.setMinimum(0);
    myProgressBar.setMaximum(totalCount);
    myProgressBar.setValue(0);
    myProgressText.setText(getDescription());
    setProgressBarVisible(true);
  }

  void complete(@NotNull FilteringResult reason) {
    if (!myIsInProcess) {
      throw new IllegalStateException("First you need to start progress");
    }

    myIsInProcess = false;
    myCompletionReason = reason;
    myProgressText.setText(getDescription());
    setProgressBarVisible(false);
  }

  private void reset() {
    myIsInProcess = false;
    myErrorCount = 0;
    myProceedCount = 0;
    myMatchedCount = 0;
    myCompletionReason = null;
  }

  private void setProgressBarVisible(boolean value) {
    myProgressPanel.setVisible(value);
  }

  private @NlsContexts.ProgressText String getDescription() {
    int total = myProgressBar.getMaximum();
    String itemsInfo = JavaDebuggerBundle.message("progress.text.shown.x.of.y", myMatchedCount, total);
    if (myIsInProcess || myCompletionReason == null) {
      return itemsInfo;
    }

    switch (myCompletionReason) {
      case ALL_CHECKED:
        break;
      case INTERRUPTED:
        itemsInfo += " " + JavaDebuggerBundle.message("progress.suffix.filtering.has.been.interrupted");
        break;
      case LIMIT_REACHED:
        itemsInfo += " " + JavaDebuggerBundle.message("progress.suffix.limit.has.been.reached");
        break;
    }

    if (myErrorCount != 0) {
      String errors = JavaDebuggerBundle.message("progress.text.errors.count", myErrorCount);
      return new HtmlBuilder().append(itemsInfo).br().append(errors).wrapWith("html").toString();
    }

    return itemsInfo;
  }
}
