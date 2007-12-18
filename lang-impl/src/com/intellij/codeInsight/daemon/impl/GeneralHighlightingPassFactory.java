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
public class GeneralHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
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
    TextRange textRange = calculateRangeToProcessForSyntaxPass(editor);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, editor.getDocument(), GeneralHighlightingPass.IN_PROGRESS_ICON,
                                                                             GeneralHighlightingPass.PRESENTABLE_NAME);
    return new GeneralHighlightingPass(myProject, file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true);
  }

  static TextRange calculateRangeToProcessForSyntaxPass(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
  }
}
