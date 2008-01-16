/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
  private MyProgressPanel myProgressPanel;
  private boolean myPaintInQueue;
  private Consumer<JComponent> myComponentUpdater;

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


  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    myProgressPanel.myFractionProgress.setIndeterminate(indeterminate);
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