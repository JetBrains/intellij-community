package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class CodeFoldingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public CodeFoldingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, Pass.UPDATE_FOLDING);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "CodeFoldingPassFactory";
  }

  @NotNull
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    return new CodeFoldingPass(myProject, editor);
  }
}
