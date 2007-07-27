package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
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
public class ExternalToolPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ExternalToolPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL, Pass.UPDATE_VISIBLE}, new int[]{Pass.LOCAL_INSPECTIONS}, true, Pass.EXTERNAL_TOOLS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExternalToolPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcessForSyntaxPass(editor);
    return textRange == null ? null : new ExternalToolPass(file, editor, textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static TextRange calculateRangeToProcessForSyntaxPass(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.EXTERNAL_TOOLS);
  }
}
