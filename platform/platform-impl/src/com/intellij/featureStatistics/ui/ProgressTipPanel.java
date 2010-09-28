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
package com.intellij.featureStatistics.ui;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.FeatureStatisticsBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeaturesRegistry;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TipUIUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressTipPanel {
  private JEditorPane myBrowser;
  private JButton myNextHintButton;
  private JButton myPrevHintButton;
  private JPanel myPanel;

  private final String[] myFeatures;
  private final Project myProject;
  private int myCurrentFeature;
  private JLabel myLabel;

  private final Alarm myMarkReadAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private static final int MARK_READ_DELAY = 10 * 1000;
  private Runnable myLastRequest;
  private JButton myCloseButton;
  private JCheckBox myKeepOpen;
  private JScrollPane myScrollPane;

  private static final @NonNls String KEEP_OPTION_NAME = "KEEP_PRODUCTIVITY_HINTS";
  private boolean myIsShownIsOwnDialog = false;

  public ProgressTipPanel(String[] features, final Project project) {
    myFeatures = features;
    myProject = project;
    myCurrentFeature = 0;

    final HTMLEditorKit editorKit = new HTMLEditorKit();
    myBrowser.setEditorKit(editorKit);
    myBrowser.setContentType(UIUtil.HTML_MIME);
    myBrowser.setBackground(UIUtil.getTextFieldBackground());

    myScrollPane.setPreferredSize(new Dimension(600, 200));
    myBrowser.setEditable(false);
    myPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
    myLabel.setIcon(IconLoader.getIcon("/general/tip.png"));
    Font font = myLabel.getFont();
    myLabel.setFont(font.deriveFont(Font.PLAIN, font.getSize() + 4));
    update();

    myCloseButton.setVisible(false);

    myPanel.addAncestorListener(new AncestorListener() {
      public void ancestorAdded(AncestorEvent event) {
      }

      public void ancestorRemoved(AncestorEvent event) {
        boolean shouldKeep = myKeepOpen.isSelected();
        PropertiesComponent.getInstance().setValue(KEEP_OPTION_NAME, String.valueOf(shouldKeep));
        if (shouldKeep) persist();
      }

      public void ancestorMoved(AncestorEvent event) {
      }
    });

    if (myFeatures.length > 1) {
      myNextHintButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myCurrentFeature < myFeatures.length - 1) {
            myCurrentFeature++;
          }
          update();
        }
      });

      myPrevHintButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myCurrentFeature > 0) {
            myCurrentFeature--;
          }
          update();
        }
      });
    }
    else {
      myNextHintButton.setVisible(false);
      myPrevHintButton.setVisible(false);
    }

    myKeepOpen.setSelected(PropertiesComponent.getInstance().isTrueValue(KEEP_OPTION_NAME));
  }

  private void persist() {
    if (myIsShownIsOwnDialog) return;
    myIsShownIsOwnDialog = true;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        new OwnDialog(myProject).show();
      }
    });
  }

  private class OwnDialog extends DialogWrapper {
    public OwnDialog(Project project) {
      super(project, false);
      setTitle(FeatureStatisticsBundle.message("feature.statistics.dialog.title"));
      setCancelButtonText(CommonBundle.getCloseButtonText());
      init();
    }

    protected JComponent createCenterPanel() {
      myKeepOpen.setVisible(false);
      myCloseButton.setVisible(true);
      myCloseButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          OwnDialog.this.close(OwnDialog.OK_EXIT_CODE);
        }
      });
      return myPanel;
    }

    protected Action[] createActions() {
      return new Action[] {};
    }
  }

  private void update() {
    myPrevHintButton.setEnabled(myCurrentFeature > 0);
    myNextHintButton.setEnabled(myCurrentFeature < myFeatures.length - 1);

    final String id = myFeatures[myCurrentFeature];

    myLastRequest = new Runnable() {
      public void run() {
        if (myLastRequest == this && !getComponent().isShowing()) return;
        FeatureUsageTracker.getInstance().triggerFeatureShown(id);
      }
    };

    myMarkReadAlarm.addRequest(myLastRequest, MARK_READ_DELAY, ModalityState.current());

    FeatureDescriptor feature = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(id);
    final String tipFileName = feature.getTipFileName();
    TipUIUtil.openTipInBrowser(tipFileName, myBrowser, feature.getProvider());
  }

  public JComponent getComponent() {
    return myPanel;
  }
}
