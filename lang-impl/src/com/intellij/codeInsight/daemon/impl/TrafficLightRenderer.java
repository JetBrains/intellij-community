package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TrafficLightRenderer implements ErrorStripeRenderer {
  private static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");
  private static final Icon NO_ANALYSIS_ICON = IconLoader.getIcon("/general/noAnalysis.png");
  private static final Icon NO_ICON = new EmptyIcon(IN_PROGRESS_ICON.getIconWidth(), IN_PROGRESS_ICON.getIconHeight());

  private final Project myProject;
  private final Document myDocument;
  private final PsiFile myFile;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @NonNls private static final String HTML_HEADER = "<html><body>";
  @NonNls private static final String HTML_FOOTER = "</body></html>";
  @NonNls private static final String BR = "<br>";
  @NonNls private static final String NO_PASS_FOR_MESSAGE_KEY_SUFFIX = ".for";

  public static class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished; // all passes done
    
    public static class PassStatus {
      private final String name;
      private final double progress;
      private final boolean finished;
      public Icon inProgressIcon;

      public PassStatus(final String name, final double progress, final boolean finished, Icon inProgressIcon) {
        this.name = name;
        this.progress = progress;
        this.finished = finished;
        this.inProgressIcon = inProgressIcon;
      }
    }
    public List<PassStatus> passStati = new ArrayList<PassStatus>(); 

    public String[/*rootsNumber*/] noHighlightingRoots;
    public String[/*rootsNumber*/] noInspectionRoots;
    public int[] errorCount;
    public int rootsNumber;

    public String toString() {
      @NonNls String s = "DS: finished=" + errorAnalyzingFinished;
      s += "; pass statuses: " + passStati.size() + "; ";
      for (PassStatus passStatus : passStati) {
        s += String.format("(%s %2.0f%% %b)", passStatus.name, passStatus.progress*100, passStatus.finished);
      }
      return s;
    }
  }

  @Nullable
  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
    if (myFile == null || myProject.isDisposed() || !myDaemonCodeAnalyzer.isHighlightingAvailable(myFile)) return null;

    ArrayList<String> noInspectionRoots = new ArrayList<String>();
    ArrayList<String> noHighlightingRoots = new ArrayList<String>();
    final PsiFile[] roots = myFile.getPsiRoots();
    for (PsiFile file : roots) {
      if (!HighlightUtil.shouldHighlight(file)) {
        noHighlightingRoots.add(file.getLanguage().getID());
      }
      else if (!HighlightUtil.shouldInspect(file)) {
        noInspectionRoots.add(file.getLanguage().getID());
      }
    }
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    status.noInspectionRoots = noInspectionRoots.isEmpty() ? null : noInspectionRoots.toArray(new String[noInspectionRoots.size()]);
    status.noHighlightingRoots = noHighlightingRoots.isEmpty() ? null : noHighlightingRoots.toArray(new String[noHighlightingRoots.size()]);

    status.errorCount = new int[SeverityRegistrar.getInstance(myProject).getSeveritiesCount()];
    status.rootsNumber = roots.length;

    for (int i = 0; i < status.errorCount.length; i++) {
      final HighlightSeverity minSeverity = SeverityRegistrar.getInstance(myProject).getSeverityByIndex(i);
      status.errorCount[i] = getErrorsCount(minSeverity);
    }
    List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(myFile);
    ArrayList<DaemonCodeAnalyzerStatus.PassStatus> passStati = new ArrayList<DaemonCodeAnalyzerStatus.PassStatus>();
    for (TextEditorHighlightingPass tepass : passes) {
      if (!(tepass instanceof ProgressableTextEditorHighlightingPass)) continue;
      ProgressableTextEditorHighlightingPass pass = (ProgressableTextEditorHighlightingPass)tepass;

      double progress = pass.getProgress();
      if (progress < 0) continue;
      DaemonCodeAnalyzerStatus.PassStatus passStatus = new DaemonCodeAnalyzerStatus.PassStatus(pass.getPresentableName(), progress, pass.isFinished(), pass.getInProgressIcon());
      passStati.add(passStatus);
    }
    status.passStati = passStati;
    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile);

    return status;
  }

  protected int getErrorsCount(final HighlightSeverity minSeverity) {
    return DaemonCodeAnalyzerImpl.getHighlights(myDocument, minSeverity, myProject).length;
  }

  public final Project getProject() {
    return myProject;
  }

  public TrafficLightRenderer(Project project, DaemonCodeAnalyzerImpl highlighter, Document document, PsiFile file) {
    myProject = project;
    myDaemonCodeAnalyzer = highlighter;
    myDocument = document;
    myFile = file;
  }

  public String getTooltipMessage() {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus();

    if (status == null) return null;
    @NonNls String text = HTML_HEADER;
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
      for (DaemonCodeAnalyzerStatus.PassStatus passStatus : status.passStati) {
        if (passStatus.finished) continue;
        text += "<tr><td>" + passStatus.name + ":</td><td>" + renderProgressHtml(passStatus.progress) +  "</td></tr>";
      }
      text += "</table>";
    }

    if (status.errorCount[0] == 0) {
      text += BR;
      if (!status.errorAnalyzingFinished) {
        text += DaemonBundle.message("no.errors.or.warnings.found.so.far");
      }
      else {
        text += DaemonBundle.message("no.errors.or.warnings.found");
      }
    }
    else {
      int currentSeverityErrors = 0;
      for (int i = status.errorCount.length - 1; i >= 0; i--) {
        final HighlightSeverity severity = SeverityRegistrar.getInstance(myProject).getSeverityByIndex(i);
        final int count = status.errorCount[i] - currentSeverityErrors;
        if (count > 0) {
          text += BR;
          String name = count > 1 ? StringUtil.pluralize(severity.toString().toLowerCase()) : severity.toString().toLowerCase();
          if (!status.errorAnalyzingFinished) {
            text += DaemonBundle.message("errors.found.so.far", count, name);
          }
          else {
            text += DaemonBundle.message("errors.found", count, name);
          }
          currentSeverityErrors += count;
        }
      }
    }

    text += getMessageByRoots(status.noHighlightingRoots, status.rootsNumber, "no.syntax.highlighting.performed");
    text += getMessageByRoots(status.noInspectionRoots, status.rootsNumber, "no.inspections.performed");
    text += HTML_FOOTER;

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
    if (roots != null) {
      final int length = roots.length;
      if (length > 0){
        if (rootsNumber > 1){
          return BR + DaemonBundle.message(prefix + NO_PASS_FOR_MESSAGE_KEY_SUFFIX, StringUtil.join(roots, ", "));
        }
        else {
          return BR + DaemonBundle.message(prefix);
        }
      }
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
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus();

    if (status == null) {
      return NO_ICON;
    }
    if (status.noHighlightingRoots != null && status.noHighlightingRoots.length == status.rootsNumber) {
      return NO_ANALYSIS_ICON;
    }
    Icon inProgressMask = null;
    boolean atLeastOnePassFinished = status.errorAnalyzingFinished;
    for (DaemonCodeAnalyzerStatus.PassStatus passStatus : status.passStati) {
      atLeastOnePassFinished |= passStatus.finished;
      if (!passStatus.finished) {
        Icon progressIcon = passStatus.inProgressIcon;
        if (progressIcon != null) {
          inProgressMask = inProgressMask == null ? progressIcon : LayeredIcon.create(inProgressMask, progressIcon);
        }
      }
    }
    Icon icon = status.errorAnalyzingFinished ? HighlightDisplayLevel.DO_NOT_SHOW.getIcon() : IN_PROGRESS_ICON;
    String sev = status.errorAnalyzingFinished ? "green" : "---";
    if (atLeastOnePassFinished) {
      SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(myProject);
      for (int i = status.errorCount.length - 1; i >= 0; i--) {
        if (status.errorCount[i] != 0) {
          icon = severityRegistrar.getRendererIconByIndex(i);
          sev = severityRegistrar.getSeverityByIndex(i).toString();
          break;
        }
      }
    }

    if (inProgressMask != null) {
      icon = LayeredIcon.create(icon, inProgressMask);
    }
    return icon;
  }
}
