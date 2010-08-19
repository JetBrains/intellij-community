/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class VisibleHighlightingPassFactory extends AbstractProjectComponent {
  public VisibleHighlightingPassFactory(Project project) {
    super(project);
  }

  @NotNull
  public static TextRange calculateVisibleRange(@NotNull Editor editor) {
    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    return new TextRange(visibleStart, visibleEnd);
  }

  @Nullable
  protected static TextRange calculateRangeToProcess(Editor editor) {
    TextRange dirtyTextRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (dirtyTextRange == null) return null;

    TextRange visibleRange = calculateVisibleRange(editor);
    TextRange textRange = dirtyTextRange.intersection(visibleRange);

    if (textRange == null || textRange.isEmpty() || textRange.equals(dirtyTextRange)) {
      return null; // no sense in highlighting the same region twice
    }
    return textRange;
  }
}
