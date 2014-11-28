package com.intellij.openapi.util.diff.tools.simple;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.diff.fragments.LineFragment;
import com.intellij.openapi.util.diff.util.Side;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.diff.util.DiffUtil.getLineCount;

class ShiftedLineFragment implements LineFragment {
  @NotNull private final LineFragment myFragment;

  @NotNull private final Document myDocument1;
  @NotNull private final Document myDocument2;

  private int myLineShift1;
  private int myLineShift2;

  public ShiftedLineFragment(@NotNull LineFragment fragment,
                             @NotNull Document document1,
                             @NotNull Document document2) {
    myFragment = fragment;
    myDocument1 = document1;
    myDocument2 = document2;

    myLineShift1 = 0;
    myLineShift2 = 0;
  }

  public void shift(int lines, @NotNull Side side) {
    if (side.isLeft()) {
      myLineShift1 += lines;
    }
    else {
      myLineShift2 += lines;
    }
  }

  public boolean intersects(int line1, int line2, @NotNull Side side) {
    if (side.getEndLine(this) <= line1) return false;
    if (side.getStartLine(this) >= line2) return false;
    return true;
  }

  @Override
  public int getStartLine1() {
    return myFragment.getStartLine1() + myLineShift1;
  }

  @Override
  public int getEndLine1() {
    return myFragment.getEndLine1() + myLineShift1;
  }

  @Override
  public int getStartLine2() {
    return myFragment.getStartLine2() + myLineShift2;
  }

  @Override
  public int getEndLine2() {
    return myFragment.getEndLine2() + myLineShift2;
  }

  @Override
  public int getStartOffset1() {
    int line = getStartLine1();
    if (line == getLineCount(myDocument1)) return myDocument1.getTextLength();
    return myDocument1.getLineStartOffset(line);
  }

  @Override
  public int getEndOffset1() {
    int line = getEndLine1() - 1;
    if (line < 0) return 0;
    if (line == getLineCount(myDocument1)) return myDocument1.getTextLength();
    return myDocument1.getLineEndOffset(line);
  }

  @Override
  public int getStartOffset2() {
    int line = getStartLine2();
    if (line == getLineCount(myDocument2)) return myDocument2.getTextLength();
    return myDocument2.getLineStartOffset(line);
  }

  @Override
  public int getEndOffset2() {
    int line = getEndLine2() - 1;
    if (line < 0) return 0;
    if (line == getLineCount(myDocument2)) return myDocument2.getTextLength();
    return myDocument2.getLineEndOffset(line);
  }
}
