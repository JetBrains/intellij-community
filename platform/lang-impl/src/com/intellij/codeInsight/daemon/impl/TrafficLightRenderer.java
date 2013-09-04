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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TrafficLightRenderer implements ErrorStripeRenderer, Disposable {
  private static final Icon IN_PROGRESS_ICON = AllIcons.General.ErrorsInProgress;
  private static final Icon NO_ANALYSIS_ICON = AllIcons.General.NoAnalysis;
  private static final Icon NO_ICON = new EmptyIcon(IN_PROGRESS_ICON.getIconWidth(), IN_PROGRESS_ICON.getIconHeight());
  private static final Icon STARING_EYE_ICON = AllIcons.General.InspectionInProgress;

  private final Project myProject;
  private final Document myDocument;
  private final PsiFile myFile;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final SeverityRegistrar mySeverityRegistrar;

  /**
   * array filled with number of highlighters with a given severity.
   * errorCount[idx] == number of highlighters of severity with index idx in this markup model.
   * severity index can be obtained via com.intellij.codeInsight.daemon.impl.SeverityRegistrar#getSeverityIdx(com.intellij.lang.annotation.HighlightSeverity)
   */
  private int[] errorCount;

  public TrafficLightRenderer(@Nullable Project project, Document document, PsiFile file) {
    myProject = project;
    myDaemonCodeAnalyzer = project == null ? null : (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    myDocument = document;
    myFile = file;
    mySeverityRegistrar = SeverityRegistrar.getSeverityRegistrar(myProject);
    refresh();

    if (project != null) {
      final MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
      model.addMarkupModelListener(this, new MarkupModelListener() {
        @Override
        public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
          incErrorCount(highlighter, 1);
        }

        @Override
        public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
          incErrorCount(highlighter, -1);
        }

        @Override
        public void attributesChanged(@NotNull RangeHighlighterEx highlighter) {
        }
      });
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          for (RangeHighlighter rangeHighlighter : model.getAllHighlighters()) {
            incErrorCount(rangeHighlighter, 1);
          }
        }
      });
    }
  }

  private void refresh() {
    int maxIndex = mySeverityRegistrar.getSeverityMaxIndex();
    if (errorCount != null && maxIndex == errorCount.length) return;
    int[] newErrors = new int[maxIndex+1];
    if (errorCount != null) {
      System.arraycopy(errorCount, 0, newErrors, 0, Math.min(errorCount.length, newErrors.length));
    }
    errorCount = newErrors;
  }

  public static void setOrRefreshErrorStripeRenderer(@NotNull EditorMarkupModel editorMarkupModel,
                                                     @NotNull Project project,
                                                     @NotNull Document document,
                                                     PsiFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!editorMarkupModel.isErrorStripeVisible() || !DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file)) {
      return;
    }
    ErrorStripeRenderer renderer = editorMarkupModel.getErrorStripeRenderer();
    if (renderer instanceof TrafficLightRenderer) {
      TrafficLightRenderer tlr = (TrafficLightRenderer)renderer;
      tlr.refresh();
      ((EditorMarkupModelImpl)editorMarkupModel).repaintVerticalScrollBar();
      if (tlr.myFile == null || tlr.myFile.isValid()) return;
      Disposer.dispose(tlr);
    }
    renderer = new TrafficLightRenderer(project, document, file);
    Disposer.register(((EditorImpl)editorMarkupModel.getEditor()).getDisposable(), (Disposable)renderer);
    editorMarkupModel.setErrorStripeRenderer(renderer);
  }

  @Override
  public void dispose() {
  }

  private void incErrorCount(RangeHighlighter highlighter, int delta) {
    Object o = highlighter.getErrorStripeTooltip();
    if (!(o instanceof HighlightInfo)) return;
    HighlightInfo info = (HighlightInfo)o;
    HighlightSeverity infoSeverity = info.getSeverity();
    final int severityIdx = mySeverityRegistrar.getSeverityIdx(infoSeverity);
    if (severityIdx != -1) {
      errorCount[severityIdx] += delta;
    }
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
    FileViewProvider provider = myFile.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    for (Language language : languages) {
      PsiFile root = provider.getPsi(language);
      if (!HighlightingLevelManager.getInstance(myProject).shouldHighlight(root)) {
        noHighlightingRoots.add(language.getID());
      }
      else if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(root)) {
        noInspectionRoots.add(language.getID());
      }
    }
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    status.noInspectionRoots = noInspectionRoots.isEmpty() ? null : ArrayUtil.toStringArray(noInspectionRoots);
    status.noHighlightingRoots = noHighlightingRoots.isEmpty() ? null : ArrayUtil.toStringArray(noHighlightingRoots);

    status.errorCount = errorCount.clone();
    status.rootsNumber = languages.size();
    fillDaemonCodeAnalyzerErrorsStatus(status, fillErrorsCount, severityRegistrar);
    List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument);
    status.passStati = passes.isEmpty() ? Collections.<ProgressableTextEditorHighlightingPass>emptyList() :
                       new ArrayList<ProgressableTextEditorHighlightingPass>(passes.size());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < passes.size(); i++) {
      TextEditorHighlightingPass tepass = passes.get(i);
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
  }

  public final Project getProject() {
    return myProject;
  }

  @Override
  public String getTooltipMessage() {
    // see TrafficProgressPanel
    return null;
  }

  @Override
  public void paint(Component c, Graphics g, Rectangle r) {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(false, mySeverityRegistrar);
    Icon icon = getIcon(status);

    int height = icon.getIconHeight();
    int width = icon.getIconWidth();
    int x = r.x + (r.width - width) / 2;
    int y = r.y + (r.height - height) / 2;
    icon.paintIcon(c, g, x, y);
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
