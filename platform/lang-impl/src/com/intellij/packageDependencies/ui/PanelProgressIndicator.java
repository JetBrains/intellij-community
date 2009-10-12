/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 13-Jul-2006
 * Time: 21:36:14
 */
package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.Consumer;

import javax.swing.*;

public class PanelProgressIndicator extends ProgressIndicatorBase {
  private final MyProgressPanel myProgressPanel;
  private boolean myPaintInQueue;
  private final Consumer<JComponent> myComponentUpdater;

  public PanelProgressIndicator(Consumer<JComponent> componentUpdater) {
    myProgressPanel = new MyProgressPanel();
    myProgressPanel.myFractionProgress.setMaximum(100);
    myComponentUpdater = componentUpdater;
  }

  public void start() {
    super.start();
    myComponentUpdater.consume(myProgressPanel.myPanel);
  }

  public void stop() {
    super.stop();
    if (isCanceled()) {
      JLabel label = new JLabel(AnalysisScopeBundle.message("usage.view.canceled"));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      myComponentUpdater.consume(label);
    }
  }

  public void setText(String text) {
    if (!text.equals(getText())) {
      super.setText(text);
      update();
    }
  }

  public void setFraction(double fraction) {
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }


  public void setIndeterminate(final boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressPanel.myFractionProgress.setIndeterminate(indeterminate);
      }
    });
  }

  private void update() {
    if (myPaintInQueue) return;
    myPaintInQueue = true;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPaintInQueue = false;
        myProgressPanel.myTextLabel.setText(getText());
        int fraction = (int)(getFraction() * 99 + 0.5);
        myProgressPanel.myFractionLabel.setText(fraction + "%");
        myProgressPanel.myFractionProgress.setValue(fraction);
      }
    });
  }

  public void setBordersVisible(final boolean visible) {
    myProgressPanel.myLeftPanel.setVisible(visible);
    myProgressPanel.myRightPanel.setVisible(visible);
  }

  private static class MyProgressPanel {
    public JLabel myFractionLabel;
    public JLabel myTextLabel;
    public JPanel myPanel;
    private JProgressBar myFractionProgress;
    private JPanel myLeftPanel;
    private JPanel myRightPanel;
  }
}
