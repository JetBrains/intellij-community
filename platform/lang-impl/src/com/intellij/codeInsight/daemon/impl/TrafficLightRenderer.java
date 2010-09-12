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
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

  @NonNls private static final String HTML_HEADER = "<html><body>";
  @NonNls private static final String HTML_FOOTER = "</body></html>";
  @NonNls private static final String BR = "<br>";
  @NonNls private static final String NO_PASS_FOR_MESSAGE_KEY_SUFFIX = ".for";

  public TrafficLightRenderer(Project project, DaemonCodeAnalyzerImpl highlighter, Document document, PsiFile file) {
    myProject = project;
    myDaemonCodeAnalyzer = highlighter;
    myDocument = document;
    myFile = file;
  }

  public static class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished; // all passes done
    public final List<ProgressableTextEditorHighlightingPass> passStati = new ArrayList<ProgressableTextEditorHighlightingPass>();
    public String[/*rootsNumber*/] noHighlightingRoots;
    public String[/*rootsNumber*/] noInspectionRoots;
    public int[] errorCount = ArrayUtil.EMPTY_INT_ARRAY;

    public int rootsNumber;
    public String toString() {
      @NonNls String s = "DS: finished=" + errorAnalyzingFinished;
      s += "; pass statuses: " + passStati.size() + "; ";
      for (ProgressableTextEditorHighlightingPass passStatus : passStati) {
        s += String.format("(%s %2.0f%% %b)", passStatus.getPresentableName(), passStatus.getProgress() *100, passStatus.isFinished());
      }
      return s;
    }
  }

  @Nullable
  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(boolean fillErrorsCount) {
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

    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(myProject);
    status.errorCount = new int[severityRegistrar.getSeveritiesCount()];
    status.rootsNumber = roots.length;
    fillDaemonCodeAnalyzerErrorsStatus(status, fillErrorsCount, severityRegistrar);
    List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument);
    for (TextEditorHighlightingPass tepass : passes) {
      if (!(tepass instanceof ProgressableTextEditorHighlightingPass)) continue;
      ProgressableTextEditorHighlightingPass pass = (ProgressableTextEditorHighlightingPass)tepass;

      if (pass.getProgress() < 0) continue;
      status.passStati.add(pass);
    }
    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile);

    return status;
  }

  protected void fillDaemonCodeAnalyzerErrorsStatus(final DaemonCodeAnalyzerStatus status,
                                                    final boolean fillErrorsCount,
                                                    final SeverityRegistrar severityRegistrar) {
    if (fillErrorsCount) Arrays.fill(status.errorCount, 0);
    final int count = severityRegistrar.getSeveritiesCount() - 1;
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
          if (infoSeverity == maxPossibleSeverity) {
            status.errorCount[count] = 1;
            return false;
          }
          if (maxFoundSeverity[0] == null || severityRegistrar.compare(maxFoundSeverity[0], infoSeverity) <= 0) {
            maxFoundSeverity[0] = infoSeverity;
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
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(true);

    if (status == null) return null;
    @NonNls String text = HTML_HEADER;
    if (PowerSaveMode.isEnabled()) {
      text += "Code analysis is disabled in power save mode.";
      text += HTML_FOOTER;
      return text;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      text += DaemonBundle.message("analysis.hasnot.been.run");
      text += HTML_FOOTER;
      return text;
    }

    if (status.errorAnalyzingFinished) {
      text += DaemonBundle.message("analysis.completed");
    }
    else {
      text += DaemonBundle.message("performing.code.analysis");
      text += "<table>";
      for (ProgressableTextEditorHighlightingPass passStatus : status.passStati) {
        if (passStatus.isFinished()) continue;
        text += "<tr><td>" + passStatus.getPresentableName() + ":</td><td>" + renderProgressHtml(passStatus.getProgress()) +  "</td></tr>";
      }
      text += "</table>";
    }

    int currentSeverityErrors = 0;
    for (int i = status.errorCount.length - 1; i >= 0; i--) {
      if (status.errorCount[i] > 0) {
        final HighlightSeverity severity = SeverityRegistrar.getInstance(myProject).getSeverityByIndex(i);
        text += BR;
        String name = status.errorCount[i] > 1 ? StringUtil.pluralize(severity.toString().toLowerCase()) : severity.toString().toLowerCase();
        text += status.errorAnalyzingFinished
                ? DaemonBundle.message("errors.found", status.errorCount[i], name)
                : DaemonBundle.message("errors.found.so.far", status.errorCount[i], name);
        currentSeverityErrors += status.errorCount[i];
      }
    }
    if (currentSeverityErrors == 0) {
      text += BR;
      text += status.errorAnalyzingFinished
              ? DaemonBundle.message("no.errors.or.warnings.found")
              : DaemonBundle.message("no.errors.or.warnings.found.so.far");
    }

    text += getMessageByRoots(status.noHighlightingRoots, status.rootsNumber, "no.syntax.highlighting.performed");
    text += getMessageByRoots(status.noInspectionRoots, status.rootsNumber, "no.inspections.performed");
    text += HTML_FOOTER;

    text = UIUtil.convertSpace2Nbsp(text);

    return text;
  }

  private static final URL progressUrl = TrafficLightRenderer.class.getClassLoader().getResource("/general/progress.png");
  private static final URL progressPlaceHolderUrl = TrafficLightRenderer.class.getClassLoader().getResource("/general/progressTransparentPlaceHolder.png");

  private static String renderProgressHtml(double progress) {
    @NonNls String text = "<table><tr><td>";
    int nBricks = 5;
    int nFilledBricks = (int)(nBricks * progress);
    int i;
    for (i = 0; i < nFilledBricks; i++) {
      text += "<img src=\"" + progressUrl + "\">";
    }
    for (; i < nBricks; i++) {
      text += "<img src=\"" + progressPlaceHolderUrl + "\">";
    }
    text += "&nbsp;"+String.format("%2.0f%%", progress * 100);
    text += "</td></tr></table>";
    return text;
  }

  private static String getMessageByRoots(String [] roots, int rootsNumber, @NonNls String prefix){
    if (roots != null && roots.length > 0) {
      return BR + (rootsNumber > 1
                   ? DaemonBundle.message(prefix + NO_PASS_FOR_MESSAGE_KEY_SUFFIX, StringUtil.join(roots, ", "))
                   : DaemonBundle.message(prefix));
    }
    return "";
  }

  public void paint(Component c, Graphics g, Rectangle r) {
    Icon icon = getIcon();

    int height = icon.getIconHeight();
    int width = icon.getIconWidth();
    icon.paintIcon(c, g, r.x + (r.width - width) / 2, r.y + (r.height - height) / 2);
  }

  private Icon getIcon() {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(false);

    if (status == null) {
      return NO_ICON;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      return NO_ANALYSIS_ICON;
    }

    boolean atLeastOnePassFinished = status.errorAnalyzingFinished;
    for (ProgressableTextEditorHighlightingPass passStatus : status.passStati) {
      atLeastOnePassFinished |= passStatus.isFinished();
    }
    Icon icon = HighlightDisplayLevel.DO_NOT_SHOW.getIcon();
    if (atLeastOnePassFinished) {
      SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(myProject);
      for (int i = status.errorCount.length - 1; i >= 0; i--) {
        if (status.errorCount[i] != 0) {
          icon = severityRegistrar.getRendererIconByIndex(i);
          break;
        }
      }
    }

    if (status.errorAnalyzingFinished) return icon;
      //icon = HighlightDisplayLevel.createIconByMask(Color.blue);
    double progress = getOverallProgress(status);
    TruncatingIcon trunc = new TruncatingIcon(icon, (int)(icon.getIconWidth() * progress), (int)(icon.getIconHeight() * progress));
    return LayeredIcon.create(trunc, STARING_EYE_ICON);
  }

  private static double getOverallProgress(DaemonCodeAnalyzerStatus status) {
    long advancement = 0;
    long limit = 0;
    for (ProgressableTextEditorHighlightingPass ps : status.passStati) {
      advancement += ps.getProgressCount();
      limit += ps.getProgressLimit();
    }
    return limit == 0 ? status.errorAnalyzingFinished ? 1 : 0 : advancement * 1.0 / limit;
  }
}
