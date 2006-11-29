package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class GeneralHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory");

  public GeneralHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this,
                                                                 new int[]{  /*Pass.POPUP_HINTS*/},
                                                                 new int[]{Pass.UPDATE_FOLDING,Pass.UPDATE_VISIBLE}, false, Pass.UPDATE_ALL);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "GeneralHighlightingPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    return new GeneralHighlightingPass(file.getProject(), file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true);
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    Document document = editor.getDocument();

    int part = FileStatusMap.NORMAL_HIGHLIGHTERS;

    PsiElement dirtyScope = DaemonCodeAnalyzer.getInstance(editor.getProject()).getFileStatusMap().getFileDirtyScope(document, part);
    int startOffset;
    int endOffset;
    if (dirtyScope != null && dirtyScope.isValid()) {
        PsiFile file = dirtyScope.getContainingFile();
        if (file.getTextLength() != document.getTextLength()) {
          LOG.error("Length wrong! dirtyScope:" + dirtyScope,
                    "file length:" + file.getTextLength(),
                    "document length:" + document.getTextLength(),
                    "file stamp:" + file.getModificationStamp(),
                    "document stamp:" + document.getModificationStamp(),
                    "file text     :" + file.getText(),
                    "document text:" + document.getText());
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Dirty block optimization works");
        }
        TextRange range = dirtyScope.getTextRange();
        startOffset = range.getStartOffset();
        endOffset = range.getEndOffset();
    }
    else {
      startOffset = 0;
      endOffset = document.getTextLength();
    }

    return new TextRange(startOffset, endOffset);
  }
}
