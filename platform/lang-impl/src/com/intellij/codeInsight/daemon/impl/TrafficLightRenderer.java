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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrafficLightRenderer implements ErrorStripeRenderer {
  private static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");
  private static final Icon NO_ANALYSIS_ICON = IconLoader.getIcon("/general/noAnalysis.png");
  private static final Icon NO_ICON = new EmptyIcon(IN_PROGRESS_ICON.getIconWidth(), IN_PROGRESS_ICON.getIconHeight());
  private static final Icon STARING_EYE_ICON = IconLoader.getIcon("/general/inspectionInProgress.png");

  private final Project myProject;
  private final Document myDocument;
  private final PsiFile myFile;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final SeverityRegistrar mySeverityRegistrar;

  public TrafficLightRenderer(Project project, DaemonCodeAnalyzerImpl highlighter, Document document, PsiFile file) {
    myProject = project;
    myDaemonCodeAnalyzer = highlighter;
    myDocument = document;
    myFile = file;
    mySeverityRegistrar = SeverityRegistrar.getInstance(myProject);
  }

  public static class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished; // all passes done
    public List<ProgressableTextEditorHighlightingPass> passStati = Collections.emptyList();
    public String[/*rootsNumber*/] noHighlightingRoots;
    public String[/*rootsNumber*/] noInspectionRoots;
    public int[] errorCount = ArrayUtil.EMPTY_INT_ARRAY;
    public boolean enabled = true;

    public int rootsNumber;
    public String toString() {
      @NonNls String s = "DS: finished=" + errorAnalyzingFinished;
      s += "; pass statuses: " + passStati.size() + "; ";
      for (ProgressableTextEditorHighlightingPass passStatus : passStati) {
        s += String.format("(%s %2.0f%% %b)", passStatus.getPresentableName(), passStatus.getProgress() *100, passStatus.isFinished());
      }
      s += "; error count: "+errorCount.length + ": "+new TIntArrayList(errorCount);
      return s;
    }
  }

  @Nullable
  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(boolean fillErrorsCount, SeverityRegistrar severityRegistrar) {
    if (myFile == null || myProject.isDisposed() || !myDaemonCodeAnalyzer.isHighlightingAvailable(myFile)) return null;

    List<String> noInspectionRoots = new ArrayList<String>();
    List<String> noHighlightingRoots = new ArrayList<String>();
    final PsiFile[] roots = myFile.getPsiRoots();
    for (PsiFile file : roots) {
      if (!HighlightLevelUtil.shouldHighlight(file)) {
        noHighlightingRoots.add(file.getLanguage().getID());
      }
      else if (!HighlightLevelUtil.shouldInspect(file)) {
        noInspectionRoots.add(file.getLanguage().getID());
      }
    }
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    status.noInspectionRoots = noInspectionRoots.isEmpty() ? null : ArrayUtil.toStringArray(noInspectionRoots);
    status.noHighlightingRoots = noHighlightingRoots.isEmpty() ? null : ArrayUtil.toStringArray(noHighlightingRoots);

    status.errorCount = new int[severityRegistrar.getSeverityMaxIndex()];
    status.rootsNumber = roots.length;
    fillDaemonCodeAnalyzerErrorsStatus(status, fillErrorsCount, severityRegistrar);
    List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument);
    status.passStati = passes.isEmpty() ? Collections.<ProgressableTextEditorHighlightingPass>emptyList() :
                       new ArrayList<ProgressableTextEditorHighlightingPass>(passes.size());
    for (TextEditorHighlightingPass tepass : passes) {
      if (!(tepass instanceof ProgressableTextEditorHighlightingPass)) continue;
      ProgressableTextEditorHighlightingPass pass = (ProgressableTextEditorHighlightingPass)tepass;

      if (pass.getProgress() < 0) continue;
      status.passStati.add(pass);
    }
    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile);
    status.enabled = myDaemonCodeAnalyzer.isUpdateByTimerEnabled();

    return status;
  }

  protected void fillDaemonCodeAnalyzerErrorsStatus(final DaemonCodeAnalyzerStatus status,
                                                    final boolean fillErrorsCount,
                                                    final SeverityRegistrar severityRegistrar) {
    final int count = severityRegistrar.getSeverityMaxIndex() - 1;
    final HighlightSeverity maxPossibleSeverity = severityRegistrar.getSeverityByIndex(count);
    final HighlightSeverity[] maxFoundSeverity = {null};

    DaemonCodeAnalyzerImpl.processHighlights(myDocument, myProject, null, 0, myDocument.getTextLength(), new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
        HighlightSeverity infoSeverity = info.getSeverity();
        if (fillErrorsCount) {
          final int severityIdx = severityRegistrar.getSeverityIdx(infoSeverity);
          if (severityIdx != -1) {
            status.errorCount[severityIdx] ++;
          }
        }
        else {
          if (maxFoundSeverity[0] == null || severityRegistrar.compare(maxFoundSeverity[0], infoSeverity) < 0) {
            maxFoundSeverity[0] = infoSeverity;
          }
          if (infoSeverity == maxPossibleSeverity) {
            return false;
          }
        }
        return true;
      }
    });
    if (maxFoundSeverity[0] != null) {
      final int severityIdx = severityRegistrar.getSeverityIdx(maxFoundSeverity[0]);
      if (severityIdx != -1) {
        status.errorCount[severityIdx] = 1;
      }
    }
  }

  public final Project getProject() {
    return myProject;
  }

  public String getTooltipMessage() {
    // see TrafficProgressPanel
    return null;
  }

  public void paint(Component c, Graphics g, Rectangle r) {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(false, mySeverityRegistrar);
    Icon icon = getIcon(status);

    int height = icon.getIconHeight();
    int width = icon.getIconWidth();
    int x = r.x + (r.width - width) / 2;
    int y = r.y + (r.height - height) / 2;
    icon.paintIcon(c, g, x, y);

    /*
    if (status != null && status.enabled && !status.errorAnalyzingFinished) {
    Color oldColor = g.getColor();
    g.setColor(Color.gray);
    //UIUtil.drawDottedRectangle(g, x, y, x + width, y + height);


    Graphics2D g2 = (Graphics2D)g;
    final Stroke saved = g2.getStroke();
    g2.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{3,1,1,1}, System.currentTimeMillis() % 400 / 100));
    g2.drawRect(x-1, y-1, width, height);
    //g2.drawRect(r.x, r.y, r.width, r.height);
    g2.setStroke(saved);

    g.setColor(oldColor);
    }
    */
  }

  private Icon getIcon(DaemonCodeAnalyzerStatus status) {
    if (status == null) {
      return NO_ICON;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      return NO_ANALYSIS_ICON;
    }

    Icon icon = HighlightDisplayLevel.DO_NOT_SHOW.getIcon();
    for (int i = status.errorCount.length - 1; i >= 0; i--) {
      if (status.errorCount[i] != 0) {
        icon = mySeverityRegistrar.getRendererIconByIndex(i);
        break;
      }
    }

    if (status.errorAnalyzingFinished) {
      if (myProject != null && DumbService.isDumb(myProject)) {
        return new LayeredIcon(NO_ANALYSIS_ICON, icon, STARING_EYE_ICON);
      }

      return icon;
    }
    if (!status.enabled) return NO_ANALYSIS_ICON;

    double progress = getOverallProgress(status);
    TruncatingIcon trunc = new TruncatingIcon(icon, icon.getIconWidth(), (int)(icon.getIconHeight() * progress));

    return new LayeredIcon(NO_ANALYSIS_ICON, trunc, STARING_EYE_ICON);
  }

  static double getOverallProgress(DaemonCodeAnalyzerStatus status) {
    long advancement = 0;
    long limit = 0;
    for (ProgressableTextEditorHighlightingPass ps : status.passStati) {
      advancement += ps.getProgressCount();
      limit += ps.getProgressLimit();
    }
    return limit == 0 ? status.errorAnalyzingFinished ? 1 : 0 : advancement * 1.0 / limit;
  }
}
