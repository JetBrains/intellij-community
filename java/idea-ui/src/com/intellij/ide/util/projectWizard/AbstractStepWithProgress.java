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

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;

import static java.awt.GridBagConstraints.*;

/**
 * @author nik
 */
public abstract class AbstractStepWithProgress<Result> extends ModuleWizardStep {
  
  @NonNls private static final String PROGRESS_PANEL = "progress_panel";
  @NonNls private static final String RESULTS_PANEL = "results_panel";
  private JPanel myPanel;
  
  private JLabel myTitleLabel;
  private JLabel myProgressLabel;
  private JLabel myProgressLabel2;
  private ProgressIndicator myProgressIndicator;
  private final String myPromptStopSearch;

  public AbstractStepWithProgress(final String promptStopSearching) {
    myPromptStopSearch = promptStopSearching;
  }

  @Override
  public final JComponent getComponent() {
    if (myPanel == null) {
      myPanel = new JPanel(new CardLayout());
      myPanel.setBorder(BorderFactory.createEtchedBorder());

      myPanel.add(createProgressPanel(), PROGRESS_PANEL);
      myPanel.add(createResultsPanel(), RESULTS_PANEL);
    }
    return myPanel;
  }

  protected abstract JComponent createResultsPanel();
  
  protected abstract String getProgressText();
  
  protected abstract boolean shouldRunProgress();
  
  protected abstract Result calculate();

  protected abstract void onFinished(Result result, boolean canceled);

  private JPanel createProgressPanel() {
    final JPanel progressPanel = new JPanel(new GridBagLayout());
    myTitleLabel = new JLabel();
    myTitleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    progressPanel.add(myTitleLabel, new GridBagConstraints(0, RELATIVE, 2, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insets(8, 10, 5, 10), 0, 0));

    myProgressLabel = new JLabel();
    progressPanel.add(myProgressLabel, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insets(8, 10, 0, 10), 0, 0));

    myProgressLabel2 = new JLabel() {
          @Override
          public void setText(String text) {
            super.setText(StringUtil.trimMiddle(text, 80));
          }
        };
    progressPanel.add(myProgressLabel2, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 1.0, NORTHWEST, HORIZONTAL, JBUI.insets(8, 10, 0, 10), 0, 0));

    JButton stopButton = new JButton(IdeBundle.message("button.stop.searching"));
    stopButton.addActionListener(__ -> cancelSearch());
    progressPanel.add(stopButton, new GridBagConstraints(1, RELATIVE, 1, 2, 0.0, 1.0, NORTHWEST, NONE, JBUI.insets(10, 0, 0, 10), 0, 0));
    return progressPanel;
  }

  @TestOnly
  public void performStep() {
    Result result = calculate();
    createResultsPanel();
    onFinished(result, false);
    updateDataModel();
  }

  private void cancelSearch() {
    if (myProgressIndicator != null) {
      myProgressIndicator.cancel();
    }
  }

  private synchronized boolean isProgressRunning() {
    return myProgressIndicator != null && myProgressIndicator.isRunning();
  }
  
  
  @Override
  public void updateStep() {
    if (shouldRunProgress()) {
      runProgress();
    }
    else {
      showCard(RESULTS_PANEL);
    }
  }

  private void runProgress() {
    final MyProgressIndicator progress = new MyProgressIndicator();
    progress.setModalityProgress(null);
    final String title = getProgressText();
    if (title != null) {
      myTitleLabel.setText(title);
    }
    showCard(PROGRESS_PANEL);
    myProgressIndicator = progress;

    if (ApplicationManager.getApplication().isUnitTestMode()) {

      Result result = ProgressManager.getInstance().runProcess(() -> calculate(), progress);
      onFinished(result, false);
      return;
    }

    UiNotifyConnector.doWhenFirstShown(myPanel, () -> new SwingWorker() {
      @Override
      public Object construct() {
        final Ref<Result> result = Ref.create(null);
        ProgressManager.getInstance().runProcess(() -> result.set(calculate()), progress);
        return result.get();
      }

      @Override
      public void finished() {
        myProgressIndicator = null;
        ApplicationManager.getApplication().invokeLater(() -> {
          final Result result = (Result)get();
          onFinished(result, progress.isCanceled());
          showCard(RESULTS_PANEL);
        });
      }
    }.start());
  }

  private void showCard(final String id) {
    ((CardLayout)myPanel.getLayout()).show(myPanel, id);
    myPanel.revalidate();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (isProgressRunning()) {
      final int answer = Messages.showOkCancelDialog(getComponent(), myPromptStopSearch,
                                             IdeBundle.message("title.question"), IdeBundle.message("action.continue.searching"), IdeBundle.message("action.stop.searching"), Messages.getWarningIcon());
      if (answer != Messages.OK) { // terminate
        cancelSearch();
      }
      return false;
    }
    return true;
  }

  @Override
  public void onStepLeaving() {
    if (isProgressRunning()) {
      cancelSearch();
    }
  }

  protected class MyProgressIndicator extends ProgressIndicatorBase {
    @Override
    public void setText(String text) {
      updateLabel(myProgressLabel, text);
      super.setText(text);
    }

    @Override
    public void setText2(String text) {
      updateLabel(myProgressLabel2, text);
      super.setText2(text);
    }

    private void updateLabel(final JLabel label, final String text) {
      UIUtil.invokeLaterIfNeeded(() -> label.setText(text));
    }
  }
}
