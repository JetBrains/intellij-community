package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
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
public class LineMarkersPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public LineMarkersPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this,
                                                                 new int[]{},
                                                                 new int[]{Pass.UPDATE_ALL}, false, -1);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "LineMarkersPassFactory";
  }
  
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcessForSyntaxPass(editor);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, editor.getDocument(), GeneralHighlightingPass.IN_PROGRESS_ICON,
                                                                             GeneralHighlightingPass.PRESENTABLE_NAME);
    return new LineMarkersPass(myProject, file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true);
  }

  @Nullable
  static TextRange calculateRangeToProcessForSyntaxPass(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
  }
}