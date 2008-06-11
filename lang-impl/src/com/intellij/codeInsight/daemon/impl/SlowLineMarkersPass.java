package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SlowLineMarkersPass extends TextEditorHighlightingPass {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<LineMarkerInfo> myMarkers;

  public SlowLineMarkersPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document, int startOffset, int endOffset) {
    super(project, document);
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    myMarkers = new SmartList<LineMarkerInfo>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      final LineMarkerProvider provider = LineMarkerProviders.INSTANCE.forLanguage(language);
      if (provider != null) {
        provider.collectSlowLineMarkers(elements, myMarkers);
      }
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, getId());

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, myFile, getId());
  }

  public Collection<LineMarkerInfo> getMarkers() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myMarkers;
  }
}

