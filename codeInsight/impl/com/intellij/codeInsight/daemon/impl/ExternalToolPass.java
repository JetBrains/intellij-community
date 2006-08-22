package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;

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
  private AnnotationHolderImpl myAnnotationHolder;
  HighlightInfoHolder myHolder;
  private Project myProject;

  public ExternalToolPass(PsiFile file,
                          Editor editor,
                          int startOffset,
                          int endOffset) {
    super(file.getProject(), editor.getDocument());
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myProject = file.getProject();
    myAnnotationHolder = new AnnotationHolderImpl();

  }

  public void doCollectInformation(ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
    for (Language language : relevantLanguages) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightUtil.shouldInspect(psiRoot)) continue;
      final List<ExternalAnnotator> externalAnnotators = language.getExternalAnnotators();

      if (externalAnnotators.size() > 0) {
        final HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject, myStartOffset, myEndOffset);

        for (HighlightInfo error : errors) {
          if (error.group != UpdateHighlightersUtil.EXTERNAL_TOOLS_HIGHLIGHTERS_GROUP) {
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
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   infos, UpdateHighlightersUtil.EXTERNAL_TOOLS_HIGHLIGHTERS_GROUP);
  }

  public List<HighlightInfo> getHighlights() {
    Annotation[] annotations = myAnnotationHolder.getResult();
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    for (Annotation annotation : annotations) {
      infos.add(HighlightUtil.convertToHighlightInfo(annotation));
    }
    return infos;
  }

  public int getPassId() {
    return Pass.EXTERNAL_TOOLS;
  }
}
