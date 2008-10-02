package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.CodeInsightSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class IdentifierHighlighterPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public IdentifierHighlighterPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{
      Pass.UPDATE_VISIBLE
    }, null, false, -1);
  }

  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    if (CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET) {
      return new IdentifierHighlighterPass(file.getProject(), file, editor);
    }
    return null;
  }
}
