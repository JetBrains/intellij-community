package com.intellij.openapi.util.diff.tools.fragmented;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.util.diff.fragments.DiffFragment;
import com.intellij.openapi.util.diff.util.DiffDrawUtil;
import com.intellij.openapi.util.diff.util.TextDiffType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class OnesideDiffChange {
  @NotNull private final EditorEx myEditor;

  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  private final int myLine1;
  private final int myLine2;

  @Nullable private final List<DiffFragment> myInnerFragments;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  public OnesideDiffChange(@NotNull EditorEx editor, @NotNull ChangedBlock block) {
    myEditor = editor;
    myStartOffset1 = block.getStartOffset1();
    myEndOffset1 = block.getEndOffset1();
    myStartOffset2 = block.getStartOffset2();
    myEndOffset2 = block.getEndOffset2();
    myLine1 = block.getLine1();
    myLine2 = block.getLine2();
    myInnerFragments = block.getInnerFragments();

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

    if (myInnerFragments != null) {
      doInstallHighlighterWithInner();
    }
    else {
      doInstallHighlighterSimple();
    }
  }

  private void doInstallHighlighterSimple() {
    createLineHighlighters(false);
  }

  private void doInstallHighlighterWithInner() {
    assert myInnerFragments != null;

    createLineHighlighters(true);

    for (DiffFragment fragment : myInnerFragments) {
      createInlineHighlighter(TextDiffType.DELETED,
                              getStartOffset1() + fragment.getStartOffset1(),
                              getStartOffset1() + fragment.getEndOffset1());
      createInlineHighlighter(TextDiffType.INSERTED,
                              getStartOffset2() + fragment.getStartOffset2(),
                              getStartOffset2() + fragment.getEndOffset2());
    }
  }

  private void createLineHighlighters(boolean ignored) {
    boolean insertion = hasInsertion();
    boolean deletion = hasDeletion();
    if (insertion && deletion) {
      createLineMarker(TextDiffType.DELETED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.DELETED, getStartOffset1(), getEndOffset1(), ignored);
      createHighlighter(TextDiffType.INSERTED, getStartOffset2(), getEndOffset2(), ignored);
      createLineMarker(TextDiffType.INSERTED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
    else if (insertion) {
      createLineMarker(TextDiffType.INSERTED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.INSERTED, getStartOffset2(), getEndOffset2(), ignored);
      createLineMarker(TextDiffType.INSERTED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
    else if (deletion) {
      createLineMarker(TextDiffType.DELETED, getLine1(), SeparatorPlacement.TOP);
      createHighlighter(TextDiffType.DELETED, getStartOffset1(), getEndOffset1(), ignored);
      createLineMarker(TextDiffType.DELETED, getLine2() - 1, SeparatorPlacement.BOTTOM);
    }
  }

  private void createHighlighter(@NotNull TextDiffType type, int start, int end, boolean ignored) {
    myHighlighters.add(DiffDrawUtil.createHighlighter(myEditor, start, end, type, ignored));
  }

  private void createInlineHighlighter(@NotNull TextDiffType type, int start, int end) {
    myHighlighters.add(DiffDrawUtil.createInlineHighlighter(myEditor, start, end, type));
  }

  private void createLineMarker(@NotNull TextDiffType type, int line, @NotNull SeparatorPlacement placement) {
    myHighlighters.add(DiffDrawUtil.createLineMarker(myEditor, line, type, placement));
  }

  public int getStartOffset1() {
    return myStartOffset1;
  }

  public int getEndOffset1() {
    return myEndOffset1;
  }

  public int getStartOffset2() {
    return myStartOffset2;
  }

  public int getEndOffset2() {
    return myEndOffset2;
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  private boolean hasInsertion() {
    return myStartOffset2 != myEndOffset2;
  }

  private boolean hasDeletion() {
    return myStartOffset1 != myEndOffset1;
  }
}
