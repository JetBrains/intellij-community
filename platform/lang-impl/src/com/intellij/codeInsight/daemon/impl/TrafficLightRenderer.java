/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class TrafficLightRenderer implements ErrorStripeRenderer, Disposable {
  private final Project myProject;
  private final Document myDocument;
  private final PsiFile myFile;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final SeverityRegistrar mySeverityRegistrar;
  private Icon icon;
  String statistics;
  String statusLabel;
  String statusExtraLine;
  boolean passStatusesVisible;
  final Map<ProgressableTextEditorHighlightingPass, Pair<JProgressBar, JLabel>> passes = ContainerUtil.newLinkedHashMap();
  static final int MAX = 100;
  boolean progressBarsEnabled;
  Boolean progressBarsCompleted;

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
      model.addMarkupModelListener(this, new MarkupModelListener.Adapter() {
        @Override
        public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
          incErrorCount(highlighter, 1);
        }

        @Override
        public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
          incErrorCount(highlighter, -1);
        }
      });
      UIUtil.invokeLaterIfNeeded(() -> {
        for (RangeHighlighter rangeHighlighter : model.getAllHighlighters()) {
          incErrorCount(rangeHighlighter, 1);
        }
      });
    }
  }

  private void refresh() {
    int maxIndex = mySeverityRegistrar.getSeverityMaxIndex();
    if (errorCount != null && maxIndex + 1 == errorCount.length) return;
    errorCount = new int[maxIndex + 1];
  }

  static void setOrRefreshErrorStripeRenderer(@NotNull EditorMarkupModel editorMarkupModel,
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
    EditorImpl editor = (EditorImpl)editorMarkupModel.getEditor();

    if (!editor.isDisposed()) {
      renderer = new TrafficLightRenderer(project, document, file);
      Disposer.register(editor.getDisposable(), (Disposable)renderer);
      editorMarkupModel.setErrorStripeRenderer(renderer);
    }
  }

  @Override
  public void dispose() {
  }

  private void incErrorCount(RangeHighlighter highlighter, int delta) {
    Object o = highlighter.getErrorStripeTooltip();
    if (!(o instanceof HighlightInfo)) return;
    HighlightInfo info = (HighlightInfo)o;
    HighlightSeverity infoSeverity = info.getSeverity();
    if (infoSeverity.myVal <= HighlightSeverity.INFORMATION.myVal) return;
    final int severityIdx = mySeverityRegistrar.getSeverityIdx(infoSeverity);
    if (severityIdx != -1) {
      errorCount[severityIdx] += delta;
    }
  }

  protected static class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished; // all passes done
    List<ProgressableTextEditorHighlightingPass> passStati = Collections.emptyList();
    public int[] errorCount = ArrayUtil.EMPTY_INT_ARRAY;
    private String reasonWhyDisabled;
    private String reasonWhySuspended;

    public DaemonCodeAnalyzerStatus() {
    }

    @Override
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

  @NotNull
  protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(@NotNull SeverityRegistrar severityRegistrar) {
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    if (myFile == null) {
      status.reasonWhyDisabled = "No file";
      status.errorAnalyzingFinished = true;
      return status;
    }
    if (myProject != null && myProject.isDisposed()) {
      status.reasonWhyDisabled = "Project is disposed";
      status.errorAnalyzingFinished = true;
      return status;
    }
    if (!myDaemonCodeAnalyzer.isHighlightingAvailable(myFile)) {
      if (!myFile.isPhysical()) {
        status.reasonWhyDisabled = "File is generated";
        status.errorAnalyzingFinished = true;
        return status;
      }
      else if (myFile instanceof PsiCompiledElement) {
        status.reasonWhyDisabled = "File is decompiled";
        status.errorAnalyzingFinished = true;
        return status;
      }
      final FileType fileType = myFile.getFileType();
      if (fileType.isBinary()) {
        status.reasonWhyDisabled = "File is binary";
        status.errorAnalyzingFinished = true;
        return status;
      }
      status.reasonWhyDisabled = "Highlighting is disabled for this file";
      status.errorAnalyzingFinished = true;
      return status;
    }

    FileViewProvider provider = myFile.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    HighlightingSettingsPerFile levelSettings = HighlightingSettingsPerFile.getInstance(myProject);
    boolean shouldHighlight = languages.isEmpty();
    for (Language language : languages) {
      PsiFile root = provider.getPsi(language);
      FileHighlightingSetting level = levelSettings.getHighlightingSettingForRoot(root);
      shouldHighlight |= level != FileHighlightingSetting.SKIP_HIGHLIGHTING;
    }
    if (!shouldHighlight) {
      status.reasonWhyDisabled = "Highlighting level is None";
      status.errorAnalyzingFinished = true;
      return status;
    }

    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      status.reasonWhySuspended = StringUtil.defaultIfEmpty(HeavyProcessLatch.INSTANCE.getRunningOperationName(), "Heavy operation is running");
      status.errorAnalyzingFinished = true;
      return status;
    }

    status.errorCount = errorCount.clone();
    fillDaemonCodeAnalyzerErrorsStatus(status, severityRegistrar);
    List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument);
    status.passStati = passes.isEmpty() ? Collections.<ProgressableTextEditorHighlightingPass>emptyList() :
                       new ArrayList<>(passes.size());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < passes.size(); i++) {
      TextEditorHighlightingPass tepass = passes.get(i);
      if (!(tepass instanceof ProgressableTextEditorHighlightingPass)) continue;
      ProgressableTextEditorHighlightingPass pass = (ProgressableTextEditorHighlightingPass)tepass;

      if (pass.getProgress() < 0) continue;
      status.passStati.add(pass);
    }
    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile);
    status.reasonWhySuspended = myDaemonCodeAnalyzer.isUpdateByTimerEnabled() ? null : "Highlighting is paused temporarily";

    return status;
  }

  protected void fillDaemonCodeAnalyzerErrorsStatus(@NotNull DaemonCodeAnalyzerStatus status,
                                                    @NotNull SeverityRegistrar severityRegistrar) {
  }

  protected final Project getProject() {
    return myProject;
  }

  @Override
  public String getTooltipMessage() {
    // see TrafficProgressPanel
    return null;
  }

  @Override
  public void paint(Component c, Graphics g, Rectangle r) {
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(mySeverityRegistrar);
    Icon icon = getIcon(status);
    icon.paintIcon(c, g, r.x, r.y);
  }

  @NotNull
  private Icon getIcon(@NotNull DaemonCodeAnalyzerStatus status) {
    updatePanel(status, getProject());
    Icon icon = this.icon;
    if (PowerSaveMode.isEnabled() || status.reasonWhySuspended != null || status.reasonWhyDisabled != null || status.errorAnalyzingFinished) {
      return icon;
    }
    return AllIcons.General.InspectionsEye;
  }

  // return true if panel needs to be rebuilt
  boolean updatePanel(@NotNull DaemonCodeAnalyzerStatus status, Project project) {
    progressBarsEnabled = false;
    progressBarsCompleted = null;
    statistics = "";
    passStatusesVisible = false;
    statusLabel = null;
    statusExtraLine = null;

    boolean result = false;
    if (!status.passStati.equals(new ArrayList<>(passes.keySet()))) {
      // passes set has changed
      rebuildPassesMap(status);
      result = true;
    }

    if (PowerSaveMode.isEnabled()) {
      statusLabel = "Code analysis is disabled in power save mode";
      status.errorAnalyzingFinished = true;
      icon = AllIcons.General.SafeMode;
      return result;
    }
    if (status.reasonWhyDisabled != null) {
      statusLabel = "No analysis has been performed";
      statusExtraLine = "(" + status.reasonWhyDisabled + ")";
      passStatusesVisible = true;
      progressBarsCompleted = Boolean.FALSE;
      icon = AllIcons.General.InspectionsTrafficOff;
      return result;
    }
    if (status.reasonWhySuspended != null) {
      statusLabel = "Code analysis has been suspended";
      statusExtraLine = "(" + status.reasonWhySuspended + ")";
      passStatusesVisible = true;
      progressBarsCompleted = Boolean.FALSE;
      icon = AllIcons.General.InspectionsPause;
      return result;
    }

    Icon icon = AllIcons.General.InspectionsOK;
    for (int i = status.errorCount.length - 1; i >= 0; i--) {
      if (status.errorCount[i] > 0) {
        icon = SeverityRegistrar.getSeverityRegistrar(project).getRendererIconByIndex(i);
        break;
      }
    }

    if (status.errorAnalyzingFinished) {
      boolean isDumb = project != null && DumbService.isDumb(project);
      if (isDumb) {
        statusLabel = "Shallow analysis completed";
        statusExtraLine = "Complete results will be available after indexing";
      }
      else {
        statusLabel = DaemonBundle.message("analysis.completed");
      }
      progressBarsCompleted = Boolean.TRUE;
    }
    else {
      statusLabel = DaemonBundle.message("performing.code.analysis");
      passStatusesVisible = true;
      progressBarsEnabled = true;
      progressBarsCompleted = null;
    }

    int currentSeverityErrors = 0;
    @org.intellij.lang.annotations.Language("HTML")
    String text = "";
    for (int i = status.errorCount.length - 1; i >= 0; i--) {
      if (status.errorCount[i] > 0) {
        final HighlightSeverity severity = SeverityRegistrar.getSeverityRegistrar(project).getSeverityByIndex(i);
        String name =
          status.errorCount[i] > 1 ? StringUtil.pluralize(severity.getName().toLowerCase()) : severity.getName().toLowerCase();
        text += status.errorAnalyzingFinished
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
    statistics = XmlStringUtil.wrapInHtml(text);

    this.icon = icon;
    return result;
  }

  private void rebuildPassesMap(@NotNull DaemonCodeAnalyzerStatus status) {
    passes.clear();
    for (ProgressableTextEditorHighlightingPass pass : status.passStati) {
      JProgressBar progressBar = new JProgressBar(0, MAX);
      progressBar.setMaximum(MAX);
      progressBar.putClientProperty("JComponent.sizeVariant", "mini");
      JLabel percLabel = new JLabel();
      percLabel.setText(TrafficProgressPanel.MAX_TEXT);
      passes.put(pass, Pair.create(progressBar, percLabel));
    }
  }
}
