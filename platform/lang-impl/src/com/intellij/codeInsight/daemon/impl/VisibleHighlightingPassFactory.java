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

public abstract class VisibleHighlightingPassFactory extends AbstractProjectComponent {
  public VisibleHighlightingPassFactory(Project project) {
    super(project);
  }

  @Nullable
  protected static TextRange calculateRangeToProcess(Editor editor) {
    TextRange dirtyTextRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (dirtyTextRange == null) return null;
    int startOffset = dirtyTextRange.getStartOffset();
    int endOffset = dirtyTextRange.getEndOffset();
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

    if (startOffset >= endOffset) {
      return null;
    }
    TextRange textRange = new TextRange(startOffset, endOffset);
    if (textRange.equals(dirtyTextRange)) {
      return null; // no sense in highlighting the same region twice
    }
    return textRange;
  }
}