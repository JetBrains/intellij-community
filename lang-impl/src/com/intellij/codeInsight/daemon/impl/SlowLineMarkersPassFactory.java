package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
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
public class SlowLineMarkersPassFactory extends AbstractProjectComponent implements DirtyScopeTrackingHighlightingPassFactory {
  public SlowLineMarkersPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, Pass.UPDATE_OVERRIDEN_MARKERS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "SlowLineMarkersPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return null;
    return new SlowLineMarkersPass(myProject, file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_OVERRIDEN_MARKERS);
  }

  public int getPassId() {
    return Pass.UPDATE_OVERRIDEN_MARKERS;
  }
}
