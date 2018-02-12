/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.memory.filtering.FilteringResult;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class FilteringProgressView extends BorderLayoutPanel {
  private final static String LIMIT_REACHED_LABEL = "Limit has been reached";
  private final static String INTERRUPTED_LABEL = "Filtering has been interrupted";

  private final JProgressBar myProgressBar = new JProgressBar();
  private final BorderLayoutPanel myProgressPanel = new BorderLayoutPanel();
  private final JBLabel myStopButton = new JBLabel(UIUtil.isUnderDarcula()
                                                   ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight);
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

  private String getDescription() {
    int total = myProgressBar.getMaximum();
    String itemsInfo = String.format("Shown: %d / %d", myMatchedCount, total);
    if (myIsInProcess || myCompletionReason == null) {
      return itemsInfo;
    }

    switch (myCompletionReason) {
      case ALL_CHECKED:
        break;
      case INTERRUPTED:
        itemsInfo = String.format("%s (%s)", itemsInfo, INTERRUPTED_LABEL);
        break;
      case LIMIT_REACHED:
        itemsInfo = String.format("%s (%s)", itemsInfo, LIMIT_REACHED_LABEL);
        break;
    }

    if (!myIsInProcess && myErrorCount != 0) {
      String errors = String.format("Errors: %d", myErrorCount);
      return String.format("<html>%s<br>%s</html>", itemsInfo, errors);
    }

    return itemsInfo;
  }
}
