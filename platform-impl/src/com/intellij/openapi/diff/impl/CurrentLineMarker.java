package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;

public class CurrentLineMarker implements CaretListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.CurrentLineMarker");
  private Editor myEditor;
  private RangeHighlighter myHighlighter = null;
  public static final int LAYER = HighlighterLayer.CARET_ROW + 1;

  public void attach(EditorSource editorSource) {
    if (myEditor != null) hide();
    myEditor = editorSource.getEditor();
    if (myEditor == null) return;
    final CaretModel caretModel = myEditor.getCaretModel();
    caretModel.addCaretListener(this);
    editorSource.addDisposable(new Disposable() {
      public void dispose() {
        caretModel.removeCaretListener(CurrentLineMarker.this);
      }
    });
  }

  public void set() {
    if (myEditor == null) return;
    hide();
    final int line = myEditor.getCaretModel().getLogicalPosition().line;
    myHighlighter = line < myEditor.getDocument().getLineCount() ? myEditor.getMarkupModel().addLineHighlighter(line, LAYER, null) : null;
  }

  private boolean isHiden() { return myHighlighter == null; }

  void hide() {
    if (myHighlighter != null) {
      LOG.assertTrue(myEditor != null);
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  public void caretPositionChanged(CaretEvent e) {
    if (isHiden()) return;
    set();
  }
}