package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
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
public class VisibleLineMarkersPassFactory extends VisibleHighlightingPassFactory implements TextEditorHighlightingPassFactory {
  public VisibleLineMarkersPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this,
                                                                 new int[]{},
                                                                 new int[]{Pass.UPDATE_VISIBLE}, false, -1);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VisibleLineMarkersPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return null;

    return new LineMarkersPass(file.getProject(), file, editor.getDocument(),
                               textRange.getStartOffset(), textRange.getEndOffset(), false);
  }
}