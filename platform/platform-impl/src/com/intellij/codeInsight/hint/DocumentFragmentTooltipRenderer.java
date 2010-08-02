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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class DocumentFragmentTooltipRenderer implements TooltipRenderer {
  private final DocumentFragment myDocumentFragment;

  public DocumentFragmentTooltipRenderer(DocumentFragment documentFragment) {
    myDocumentFragment = documentFragment;
  }

  public LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group) {
    LightweightHint hint;

    final JComponent editorComponent = editor.getComponent();

    TextRange range = myDocumentFragment.getTextRange();
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    Document doc = myDocumentFragment.getDocument();
    int endLine = doc.getLineNumber(endOffset);
    int startLine = doc.getLineNumber(startOffset);

    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    // There is a possible case that collapsed folding region is soft wrapped, hence, we need to anchor
    // not logical but visual line start.
    VisualPosition visual = editor.offsetToVisualPosition(startOffset);
    p = editor.visualPositionToXY(visual);
    p = SwingUtilities.convertPoint(
      ((EditorEx)editor).getGutterComponentEx(),
      p,
      layeredPane
    );

    p.x -= 3;
    p.y += editor.getLineHeight();

    Point screen = new Point(p);
    SwingUtilities.convertPointToScreen(screen, layeredPane);
    int maxLineCount = (Toolkit.getDefaultToolkit().getScreenSize().height - screen.y) / editor.getLineHeight();

    if (endLine - startLine > maxLineCount) {
      endOffset = doc.getLineEndOffset(Math.min(startLine + maxLineCount, doc.getLineCount() - 1));
    }

    FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    foldingModel.setFoldingEnabled(false);
    TextRange textRange = new TextRange(startOffset, endOffset);
    hint = EditorFragmentComponent.showEditorFragmentHintAt(editor, textRange, p.x, p.y, false, false, true);
    foldingModel.setFoldingEnabled(true);
    return hint;
  }
}
