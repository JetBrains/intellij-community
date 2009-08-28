package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentFragment;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Feb 21, 2005
 * Time: 7:14:29 PM
 * To change this template use File | Settings | File Templates.
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

    p = editor.logicalPositionToXY(new LogicalPosition(startLine, 0));
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
    hint = EditorFragmentComponent.showEditorFragmentHintAt(editor, textRange, p.x, p.y, false, false);
    foldingModel.setFoldingEnabled(true);
    return hint;
  }
}
