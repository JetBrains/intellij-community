/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class VisibleHighlightingPassFactory extends AbstractProjectComponent {
  public VisibleHighlightingPassFactory(Project project) {
    super(project);
  }

  @Nullable
  protected static TextRange calculateRangeToProcess(Editor editor) {
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