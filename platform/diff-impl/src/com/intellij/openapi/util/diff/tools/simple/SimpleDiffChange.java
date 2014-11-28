package com.intellij.openapi.util.diff.tools.simple;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.fragments.DiffFragment;
import com.intellij.openapi.util.diff.fragments.FineLineFragment;
import com.intellij.openapi.util.diff.fragments.LineFragment;
import com.intellij.openapi.util.diff.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class SimpleDiffChange {
  @NotNull private final LineFragment myFragment;
  @Nullable private final List<DiffFragment> myFineFragments;
  @Nullable private final ShiftedLineFragment myShiftedFragment;

  @Nullable private final EditorEx myEditor1;
  @Nullable private final EditorEx myEditor2;

  @NotNull private final List<RangeHighlighter> myHighlighters;
  @NotNull private final List<RangeHighlighter> myActionHighlighters;

  private boolean myIsValid = true;

  // TODO: adjust color from inner fragments - configurable
  public SimpleDiffChange(@NotNull LineFragment fragment,
                          @Nullable EditorEx editor1,
                          @Nullable EditorEx editor2,
                          boolean inlineHighlight) {
    myFragment = fragment;
    myFineFragments = inlineHighlight && fragment instanceof FineLineFragment ? ((FineLineFragment)fragment).getFineFragments() : null;

    myEditor1 = editor1;
    myEditor2 = editor2;

    myHighlighters = new ArrayList<RangeHighlighter>();
    myActionHighlighters = new ArrayList<RangeHighlighter>();

    if (editor1 != null && editor2 != null) {
      myShiftedFragment = new ShiftedLineFragment(fragment, editor1.getDocument(), editor2.getDocument());
    }
    else {
      myShiftedFragment = null;
    }

    installHighlighter();
  }

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull Side side) {
    if (myShiftedFragment == null || myShiftedFragment.intersects(oldLine1, oldLine2, side)) {
      for (RangeHighlighter highlighter : myActionHighlighters) {
        highlighter.dispose();
      }
      myActionHighlighters.clear();
      myIsValid = false;
      return true;
    }

    if (oldLine2 <= side.getStartLine(myShiftedFragment)) {
      myShiftedFragment.shift(shift, side);
    }
    return false;
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    if (myFineFragments != null) {
      doInstallHighlighterWithInner();
    }
    else {
      doInstallHighlighterSimple();
    }
    doInstallActionHighlighters();
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (RangeHighlighter highlighter : myActionHighlighters) {
      highlighter.dispose();
    }
    myActionHighlighters.clear();
  }

  private void doInstallHighlighterSimple() {
    createHighlighter(Side.LEFT, false);
    createHighlighter(Side.RIGHT, false);
  }

  private void doInstallHighlighterWithInner() {
    assert myFineFragments != null;

    createHighlighter(Side.LEFT, true);
    createHighlighter(Side.RIGHT, true);

    for (DiffFragment fragment : myFineFragments) {
      createInlineHighlighter(fragment, Side.LEFT);
      createInlineHighlighter(fragment, Side.RIGHT);
    }
  }

  private void doInstallActionHighlighters() {
    if (myEditor1 != null && myEditor2 != null) {
      if (DiffUtil.canMakeWritable(myEditor1.getDocument())) {
        MyReplaceOperation operation = new MyReplaceOperation(Side.LEFT);
        myActionHighlighters.add(DiffOperation.createHighlighter(myEditor2, operation, myFragment.getStartOffset2()));
      }
      if (DiffUtil.canMakeWritable(myEditor2.getDocument())) {
        MyReplaceOperation operation = new MyReplaceOperation(Side.RIGHT);
        myActionHighlighters.add(DiffOperation.createHighlighter(myEditor1, operation, myFragment.getStartOffset1()));
      }
    }
  }

  private void createHighlighter(@NotNull Side side, boolean ignored) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;

    int start = side.getStartOffset(myFragment);
    int end = side.getEndOffset(myFragment);
    TextDiffType type = getDiffType(myFragment);

    myHighlighters.add(DiffDrawUtil.createHighlighter(editor, start, end, type, ignored));

    int startLine = side.getStartLine(myFragment);
    int endLine = side.getEndLine(myFragment);

    if (startLine == endLine) {
      if (startLine != 0) myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM, true));
    }
    else {
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, startLine, type, SeparatorPlacement.TOP));
      myHighlighters.add(DiffDrawUtil.createLineMarker(editor, endLine - 1, type, SeparatorPlacement.BOTTOM));
    }
  }

  private void createInlineHighlighter(@NotNull DiffFragment fragment, @NotNull Side side) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;

    int start = side.getStartOffset(fragment);
    int end = side.getEndOffset(fragment);
    TextDiffType type = getDiffType(fragment);

    int startOffset = side.getStartOffset(myFragment);
    start += startOffset;
    end += startOffset;

    RangeHighlighter highlighter = DiffDrawUtil.createInlineHighlighter(editor, start, end, type);
    myHighlighters.add(highlighter);
  }

  //
  // Types
  //

  @NotNull
  private static TextDiffType getDiffType(@NotNull LineFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1() || fragment.getStartLine1() != fragment.getEndLine1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2() || fragment.getStartLine2() != fragment.getEndLine2();
    return getType(left, right);
  }

  @NotNull
  private static TextDiffType getDiffType(@NotNull DiffFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2();
    return getType(left, right);
  }

  private static TextDiffType getType(boolean left, boolean right) {
    if (left && right) {
      return TextDiffType.MODIFIED;
    }
    else if (left) {
      return TextDiffType.DELETED;
    }
    else if (right) {
      return TextDiffType.INSERTED;
    }
    else {
      throw new IllegalArgumentException();
    }
  }

  @NotNull
  public LineFragment getFragment() {
    return myShiftedFragment != null ? myShiftedFragment : myFragment;
  }

  @NotNull
  public TextDiffType getDiffType() {
    return getDiffType(myFragment);
  }

  //
  // Change applying
  //

  public boolean isSelectedByLine(int line, @NotNull Side side) {
    if (myEditor1 == null || myEditor2 == null) return false;
    assert myShiftedFragment != null;

    int line1 = side.getStartLine(myShiftedFragment);
    int line2 = side.getEndLine(myShiftedFragment);

    return DiffUtil.isSelectedByLine(line, line1, line2);
  }

  @CalledWithWriteLock
  public void replaceChange(@NotNull final Side sourceSide) {
    assert myEditor1 != null && myEditor2 != null;
    assert myShiftedFragment != null;

    if (!myIsValid) return;

    final Document document1 = myEditor1.getDocument();
    final Document document2 = myEditor2.getDocument();

    DiffUtil.applyModification(sourceSide.other().selectN(document1, document2), sourceSide.other().getStartLine(myShiftedFragment),
                               sourceSide.other().getEndLine(myShiftedFragment), sourceSide.selectN(document1, document2),
                               sourceSide.getStartLine(myShiftedFragment), sourceSide.getEndLine(myShiftedFragment));

    destroyHighlighter();
  }

  @CalledWithWriteLock
  public void appendChange(@NotNull final Side sourceSide) {
    assert myEditor1 != null && myEditor2 != null;
    assert myShiftedFragment != null;

    if (!myIsValid) return;

    destroyHighlighter();

    final Document document1 = myEditor1.getDocument();
    final Document document2 = myEditor2.getDocument();

    DiffUtil.applyModification(sourceSide.other().selectN(document1, document2), sourceSide.other().getEndLine(myShiftedFragment),
                               sourceSide.other().getEndLine(myShiftedFragment), sourceSide.selectN(document1, document2),
                               sourceSide.getStartLine(myShiftedFragment), sourceSide.getEndLine(myShiftedFragment));

    destroyHighlighter();
  }

  //
  // Helpers
  //

  private class MyReplaceOperation extends DiffOperation {
    @NotNull private final Side mySide;

    public MyReplaceOperation(@NotNull Side side) {
      super("Replace", AllIcons.Diff.Arrow);
      mySide = side;
    }

    @Override
    public void perform(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      assert myEditor1 != null && myEditor2 != null;

      final Document document1 = myEditor1.getDocument();
      final Document document2 = myEditor2.getDocument();

      if (!myIsValid) return;

      DiffUtil.executeWriteCommand(mySide.selectN(document1, document2), project, "Replace change", new Runnable() {
        @Override
        public void run() {
          replaceChange(mySide.other());
        }
      });
    }
  }
}
