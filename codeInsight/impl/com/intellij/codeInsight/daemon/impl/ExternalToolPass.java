package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class ExternalToolPass extends TextEditorHighlightingPass {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final AnnotationHolderImpl myAnnotationHolder;

  public ExternalToolPass(@NotNull PsiFile file,
                          @NotNull Editor editor,
                          int startOffset,
                          int endOffset) {
    super(file.getProject(), editor.getDocument());
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myAnnotationHolder = new AnnotationHolderImpl();
  }

  public void doCollectInformation(ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
    for (Language language : relevantLanguages) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightUtil.shouldInspect(psiRoot)) continue;
      final List<ExternalAnnotator> externalAnnotators = language.getExternalAnnotators();

      if (!externalAnnotators.isEmpty()) {
        final HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject, myStartOffset, myEndOffset);

        for (HighlightInfo error : errors) {
          if (error.group != Pass.EXTERNAL_TOOLS) {
            return;
          }
        }
        for(ExternalAnnotator externalAnnotator: externalAnnotators) {
          externalAnnotator.annotate(psiRoot, myAnnotationHolder);
        }
      }
    }
  }

  public void doApplyInformationToEditor() {
    List<HighlightInfo> infos = getHighlights();

    // This should be done for any result for removing old highlights
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, infos, Pass.EXTERNAL_TOOLS);
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, getId());
  }

  public List<HighlightInfo> getHighlights() {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    for (Annotation annotation : myAnnotationHolder) {
      infos.add(HighlightUtil.convertToHighlightInfo(annotation));
    }
    return infos;
  }
}
