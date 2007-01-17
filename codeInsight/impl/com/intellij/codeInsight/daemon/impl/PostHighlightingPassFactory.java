package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
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
public class PostHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public PostHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{
      Pass.UPDATE_ALL,
    }, null, true, Pass.POST_UPDATE_ALL);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "PostHighlightingPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    return new PostHighlightingPass(myProject, file, editor, textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    Document document = editor.getDocument();

    int startOffset = 0;
    int endOffset = document.getTextLength();
    return new TextRange(startOffset, endOffset);
  }
}
