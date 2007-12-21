package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class VisibleGeneralHighlightingPassFactory extends VisibleHighlightingPassFactory implements TextEditorHighlightingPassFactory {
  public VisibleGeneralHighlightingPassFactory(Project project,  TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_FOLDING,}, null, true, Pass.UPDATE_VISIBLE);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VisibleHighlightingPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return null;

    final TextEditorHighlightingPass general = new GeneralHighlightingPass(file.getProject(), file, editor.getDocument(),
                                                                           textRange.getStartOffset(), textRange.getEndOffset(), false);
    final TextEditorHighlightingPass linemarkers = new LineMarkersPass(file.getProject(), file, editor.getDocument(),
                                                                       textRange.getStartOffset(), textRange.getEndOffset(), false);
    return new CompositeTextEditorHighlightingPass(file.getProject(),  editor.getDocument(), general, linemarkers);
  }

}
