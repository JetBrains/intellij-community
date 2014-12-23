package com.intellij.openapi.util.diff.tools.fragmented;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.diff.util.DiffDrawUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class OnesideDiffSeparator {
  @NotNull private final Editor myEditor;

  private final int myLine;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  public OnesideDiffSeparator(@NotNull Editor editor, int line) {
    myEditor = editor;
    myLine = line;
    installHighlighter();
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    int offset = DocumentUtil.getFirstNonSpaceCharOffset(myEditor.getDocument(), myLine);
    myHighlighters.add(DiffDrawUtil.createLineSeparatorHighlighter(myEditor, offset));
  }

  public int getLine() {
    return myLine;
  }
}
