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
public class LocalInspectionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public LocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL/*, Pass.POPUP_HINTS*/}, true, Pass.LOCAL_INSPECTIONS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "LocalInspectionsPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, editor.getDocument(), LocalInspectionsPass.IN_PROGRESS_ICON,
                                                                             LocalInspectionsPass.PRESENTABLE_NAME);
    return new LocalInspectionsPass(file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.LOCAL_INSPECTIONS);
  }
}
