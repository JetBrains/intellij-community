// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.ui;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.FeatureStatisticsBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.ide.util.TipUIUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class AdaptiveTipDialog extends DialogWrapper {
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;

  private TipUIUtil.Browser myBrowser;
  private final String[] myFeatures;
  private int myCurrentFeature;

  public AdaptiveTipDialog(Project project, String[] features) {
    super(project, false);
    myFeatures = features;
    myCurrentFeature = 0;
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setTitle(FeatureStatisticsBundle.message("feature.statistics.dialog.title"));
    setModal(false);
    init();
    selectCurrentFeature();
  }

  private void selectCurrentFeature() {
    String id = myFeatures[myCurrentFeature];
    FeatureUsageTracker.getInstance().triggerFeatureShown(id);

    FeatureDescriptor feature = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(id);
    TipUIUtil.openTipInBrowser(feature.getTipFileName(), myBrowser, feature.getProvider());
  }


  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myBrowser = TipUIUtil.createBrowser();

    panel.add(ScrollPaneFactory.createScrollPane(myBrowser.getComponent()));
    panel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    return panel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    if (myFeatures.length == 1) {
      return new Action[] {getCancelAction()};
    }
    else {
      return new Action[] {new PrevAction(), new NextAction(), getCancelAction()};
    }
  }

  private class NextAction extends AbstractAction{
    public NextAction() {
      super(FeatureStatisticsBundle.message("feature.statistics.action.next.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCurrentFeature++;
      if (myCurrentFeature >= myFeatures.length) {
        myCurrentFeature = 0;
      }
      selectCurrentFeature();
    }
  }

  private class PrevAction extends AbstractAction{
    public PrevAction() {
      super(FeatureStatisticsBundle.message("feature.statistics.action.prev.tip"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCurrentFeature--;
      if (myCurrentFeature < 0) {
        myCurrentFeature = myFeatures.length - 1;
      }
      selectCurrentFeature();
    }
  }
}
