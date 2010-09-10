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
import com.intellij.codeInsight.hint.LineTooltipRenderer;
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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.containers.hash.LinkedHashMap;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: cdr
 */
public class TrafficProgressPanel extends JPanel {
  private final JLabel statistics = new JLabel();
  private final Map<ProgressableTextEditorHighlightingPass, Pair<JProgressBar, JLabel>> passes = new LinkedHashMap<ProgressableTextEditorHighlightingPass, Pair<JProgressBar, JLabel>>();
  private final JLabel statusLabel = new JLabel();
  private final TrafficLightRenderer myTrafficLightRenderer;
  private final JPanel passStatuses = new JPanel();

  public TrafficProgressPanel(TrafficLightRenderer trafficLightRenderer, Editor editor) {
    super(new VerticalFlowLayout());
    myTrafficLightRenderer = trafficLightRenderer;

    LineTooltipRenderer.setColors(this);
    LineTooltipRenderer.setBorder(this);
    LineTooltipRenderer.setColors(passStatuses);

    add(statusLabel);
    TrafficLightRenderer.DaemonCodeAnalyzerStatus fakeStatusLargeEnough = new TrafficLightRenderer.DaemonCodeAnalyzerStatus();
    fakeStatusLargeEnough.errorCount = new int[]{1,1,1,1};
    Project project = trafficLightRenderer.getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
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
      bar.setMaximum(100);
      JLabel label = pair.second;
      label.setText("100%");
    }
    add(passStatuses);

    add(statistics);
    updatePanel(fakeStatusLargeEnough);
  }

  private void rebuildPassesPanel(TrafficLightRenderer.DaemonCodeAnalyzerStatus status) {
    passStatuses.removeAll();
    passStatuses.setLayout(new GridLayoutManager(Math.max(status.passStati.size(),1), 3));
    passes.clear();
    GridConstraints constraints = new GridConstraints();
    for (ProgressableTextEditorHighlightingPass pass : status.passStati) {
      JLabel label = new JLabel(pass.getPresentableName() + ":");
      JProgressBar progressBar = new JProgressBar(0,100);
      JLabel percLabel = new JLabel();
      passes.put(pass, Pair.create(progressBar, percLabel));
      constraints.setColumn(0); passStatuses.add(label, constraints);
      constraints.setColumn(1); passStatuses.add(progressBar, constraints);
      constraints.setColumn(2); passStatuses.add(percLabel, constraints);
      constraints.setRow(constraints.getRow()+1);
    }
  }

  public void updatePanel(TrafficLightRenderer.DaemonCodeAnalyzerStatus status) {
    if (status == null) return;
    if (PowerSaveMode.isEnabled()) {
      statusLabel.setText("Code analysis is disabled in power save mode.");
      passStatuses.setVisible(false);
      statistics.setText("");
      return;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      statusLabel.setText(DaemonBundle.message("analysis.hasnot.been.run"));
      passStatuses.setVisible(false);
      statistics.setText("");
      return;
    }

    if (status.errorAnalyzingFinished) {
      statusLabel.setText(DaemonBundle.message("analysis.completed"));
      passStatuses.setVisible(false);
    }
    else {
      statusLabel.setText(DaemonBundle.message("performing.code.analysis"));
      passStatuses.setVisible(true);

      if (!status.passStati.equals(new ArrayList<ProgressableTextEditorHighlightingPass>(passes.keySet()))) {
        // passes set has changed
        rebuildPassesPanel(status);
      }

      for (ProgressableTextEditorHighlightingPass pass : status.passStati) {
        double progress = pass.getProgress();
        Pair<JProgressBar, JLabel> pair = passes.get(pass);
        JProgressBar progressBar = pair.first;
        int percent = (int)Math.round(progress * 100);
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
}
