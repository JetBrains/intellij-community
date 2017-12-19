/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentFragment;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.NotNull;

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

  @Override
  public LightweightHint show(@NotNull final Editor editor, @NotNull Point p, boolean alignToRight, @NotNull TooltipGroup group, @NotNull HintHint intInfo) {
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

    Point screenPoint = new Point(p);
    SwingUtilities.convertPointToScreen(screenPoint, layeredPane);
    int maxLineCount = (ScreenUtil.getScreenRectangle(screenPoint).height - screenPoint.y) / editor.getLineHeight();

    if (endLine - startLine > maxLineCount) {
      endOffset = doc.getLineEndOffset(Math.max(0, Math.min(startLine + maxLineCount, doc.getLineCount() - 1)));
    }
    if (endOffset < startOffset) return null;

    TextRange textRange = new TextRange(startOffset, endOffset);
    return EditorFragmentComponent.showEditorFragmentHintAt(editor, textRange, p.y, false, false, true, true, true);
  }
}
