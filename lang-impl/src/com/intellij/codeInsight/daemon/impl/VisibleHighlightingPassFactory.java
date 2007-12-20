package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author cdr
*/
public class VisibleHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public VisibleHighlightingPassFactory(Project project,  TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
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

  @Nullable
  private static TextRange calculateRangeToProcess(Editor editor) {
    TextRange range = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (range == null) return null;
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    if (visibleStart > startOffset) {
      startOffset = visibleStart;
    }
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    if (visibleEnd < endOffset) {
      endOffset = visibleEnd;
    }

    return startOffset < endOffset ? new TextRange(startOffset, endOffset) : null;
  }
}
