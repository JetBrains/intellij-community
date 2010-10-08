/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.AwtVisitor;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: cdr
 */
public class TrafficProgressPanel extends JPanel {

  private static final int MAX = 100;
  private static final String MAX_TEXT = "100%";
  private static final String MIN_TEXT = "0%";

  private final JLabel statistics = new JLabel();
  private final Map<ProgressableTextEditorHighlightingPass, Pair<JProgressBar, JLabel>> passes = new LinkedHashMap<ProgressableTextEditorHighlightingPass, Pair<JProgressBar, JLabel>>();
  private Map<JProgressBar, JLabel> myProgressToText = new HashMap<JProgressBar, JLabel>();

  private final JLabel statusLabel = new JLabel();
  private final TrafficLightRenderer myTrafficLightRenderer;
  private final JPanel passStatuses = new JPanel();
  private HintHint myHintHint;

  public TrafficProgressPanel(TrafficLightRenderer trafficLightRenderer, Editor editor, HintHint hintHint) {
    super(new VerticalFlowLayout());
    myHintHint = hintHint;
    myTrafficLightRenderer = trafficLightRenderer;


    add(statusLabel);
    TrafficLightRenderer.DaemonCodeAnalyzerStatus fakeStatusLargeEnough = new TrafficLightRenderer.DaemonCodeAnalyzerStatus();
    fakeStatusLargeEnough.errorCount = new int[]{1,1,1,1};
    Project project = trafficLightRenderer.getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    fakeStatusLargeEnough.passStati = new ArrayList<ProgressableTextEditorHighlightingPass>();
    for (int i=0; i<3;i++) {
      fakeStatusLargeEnough.passStati.add(new ProgressableTextEditorHighlightingPass(project, null, DaemonBundle.message("pass.wolf"), psiFile, false) {
        @Override
        protected void collectInformationWithProgress(ProgressIndicator progress) {
        }

        @Override
        protected void applyInformationWithProgress() {
        }
      });
    }
    rebuildPassesPanel(fakeStatusLargeEnough);
    for (Pair<JProgressBar, JLabel> pair : passes.values()) {
      JProgressBar bar = pair.first;
      bar.setMaximum(MAX);
      JLabel label = pair.second;
      label.setText(MAX_TEXT);
    }
    add(passStatuses);

    add(new Separator());

    add(statistics);
    updatePanel(fakeStatusLargeEnough);

    statistics.setBorder(new LineBorder(Color.red));

    hintHint.initStyle(this, true);
  }

  private class Separator extends NonOpaquePanel {
    @Override
    protected void paintComponent(Graphics g) {
      Insets insets = getInsets();
      if (insets == null) {
        insets = new Insets(0, 0, 0, 0);
      }
      g.setColor(myHintHint.getTextForeground());
      g.drawLine(insets.left, insets.top, getWidth() - insets.left - insets.right, insets.top);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(1, 1);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(1, 1);
    }
  }

  private void rebuildPassesPanel(TrafficLightRenderer.DaemonCodeAnalyzerStatus status) {
    passStatuses.removeAll();
    passStatuses.setLayout(new GridLayoutManager(Math.max(status.passStati.size(),1), 3));
    passes.clear();
    GridConstraints constraints = new GridConstraints();
    for (ProgressableTextEditorHighlightingPass pass : status.passStati) {
      JLabel label = new JLabel(pass.getPresentableName() + ":");

      JProgressBar progressBar = new JProgressBar(0, MAX);
      progressBar.putClientProperty("JComponent.sizeVariant", "mini");
      JLabel percLabel = new JLabel();
      passes.put(pass, Pair.create(progressBar, percLabel));
      myProgressToText.put(progressBar, percLabel);
      constraints.setColumn(0); passStatuses.add(label, constraints);
      constraints.setColumn(1); passStatuses.add(progressBar, constraints);
      constraints.setColumn(2); passStatuses.add(percLabel, constraints);
      constraints.setRow(constraints.getRow()+1);
    }

    myHintHint.initStyle(passStatuses, true);
  }

  public void updatePanel(TrafficLightRenderer.DaemonCodeAnalyzerStatus status) {
    if (status == null) return;
    if (PowerSaveMode.isEnabled()) {
      statusLabel.setText("Code analysis is disabled in power save mode");
      passStatuses.setVisible(false);
      statistics.setText("");
      return;
    }
    if (!status.enabled) {
      statusLabel.setText("Code analysis has been suspended");
      passStatuses.setVisible(true);
      setPassesEnabled(false, Boolean.FALSE);
      statistics.setText("");
      return;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      statusLabel.setText(DaemonBundle.message("analysis.hasnot.been.run"));
      passStatuses.setVisible(true);
      setPassesEnabled(false, Boolean.FALSE);
      statistics.setText("");
      return;
    }

    if (status.errorAnalyzingFinished) {
      statusLabel.setText(DaemonBundle.message("analysis.completed"));
      passStatuses.setVisible(true);
      setPassesEnabled(false, Boolean.TRUE);
    }
    else {
      statusLabel.setText(DaemonBundle.message("performing.code.analysis"));
      passStatuses.setVisible(true);
      setPassesEnabled(true, null);

      if (!status.passStati.equals(new ArrayList<ProgressableTextEditorHighlightingPass>(passes.keySet()))) {
        // passes set has changed
        rebuildPassesPanel(status);
      }

      for (ProgressableTextEditorHighlightingPass pass : status.passStati) {
        double progress = pass.getProgress();
        Pair<JProgressBar, JLabel> pair = passes.get(pass);
        JProgressBar progressBar = pair.first;
        int percent = (int)Math.round(progress * MAX);
        progressBar.setValue(percent);
        JLabel percentage = pair.second;
        percentage.setText(percent + "%");
      }
    }

    int currentSeverityErrors = 0;
    String text = "<html><body>";
    for (int i = status.errorCount.length - 1; i >= 0; i--) {
      if (status.errorCount[i] > 0) {
        final HighlightSeverity severity = SeverityRegistrar.getInstance(myTrafficLightRenderer.getProject()).getSeverityByIndex(i);
        String name = status.errorCount[i] > 1 ? StringUtil.pluralize(severity.toString().toLowerCase()) : severity.toString().toLowerCase();
        text +=  status.errorAnalyzingFinished
                ? DaemonBundle.message("errors.found", status.errorCount[i], name)
                : DaemonBundle.message("errors.found.so.far", status.errorCount[i], name);
        text += "<br>";
        currentSeverityErrors += status.errorCount[i];
      }
    }
    if (currentSeverityErrors == 0) {
      text += status.errorAnalyzingFinished
              ? DaemonBundle.message("no.errors.or.warnings.found")
              : DaemonBundle.message("no.errors.or.warnings.found.so.far") + "<br>";
    }
    statistics.setText(text);
  }

  private void setPassesEnabled(final boolean enabled, final Boolean completed) {
    new AwtVisitor(passStatuses) {
      @Override
      public boolean visit(Component component) {
        if (component instanceof JProgressBar) {
          JProgressBar progress = (JProgressBar)component;
          progress.setEnabled(enabled);
          if (completed != null) {
            if (completed) {
              progress.setValue(MAX);
              myProgressToText.get(progress).setText(MAX_TEXT);
            } else {
              progress.setValue(0);
              myProgressToText.get(progress).setText(MIN_TEXT);
            }
          }
        }
        return false;
      }
    };
  }
}
