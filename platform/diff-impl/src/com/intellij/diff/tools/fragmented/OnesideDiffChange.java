/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.TextDiffType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class OnesideDiffChange {
  @NotNull private final OnesideDiffViewer myViewer;
  @NotNull private final EditorEx myEditor;

  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  private final int myLine1;
  private final int myLine2;

  @NotNull private final LineFragment myLineFragment;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<MyGutterOperation>();

  public OnesideDiffChange(@NotNull OnesideDiffViewer viewer, @NotNull ChangedBlock block, boolean innerFragments) {
    myViewer = viewer;
    myEditor = viewer.getEditor();

    myStartOffset1 = block.getStartOffset1();
    myEndOffset1 = block.getEndOffset1();
    myStartOffset2 = block.getStartOffset2();
    myEndOffset2 = block.getEndOffset2();
    myLine1 = block.getLine1();
    myLine2 = block.getLine2();
    myLineFragment = block.getLineFragment();

    installHighlighter(innerFragments);
  }

  public void destroyHighlighter() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (MyGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  public void installHighlighter(boolean innerFragments) {
    assert myHighlighters.isEmpty();

    if (innerFragments && myLineFragment.getInnerFragments() != null) {
      doInstallHighlighterWithInner();
    }
    else {
      doInstallHighlighterSimple();
    }
    doInstallActionHighlighters();
  }

  private void doInstallActionHighlighters() {
    if (myViewer.getDocument(Side.LEFT) == null || myViewer.getDocument(Side.RIGHT) == null) return;

    boolean leftEditable = myViewer.isEditable(Side.LEFT, false);
    boolean rightEditable = myViewer.isEditable(Side.RIGHT, false);

    if (rightEditable) myOperations.add(createOperation(Side.LEFT, false));
    if (leftEditable) myOperations.add(createOperation(Side.RIGHT, rightEditable));
  }

  private void doInstallHighlighterSimple() {
    createLineHighlighters(false);
  }

  private void doInstallHighlighterWithInner() {
    List<DiffFragment> innerFragments = myLineFragment.getInnerFragments();
    assert innerFragments != null;

    createLineHighlighters(true);

    for (DiffFragment fragment : innerFragments) {
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

  @CalledWithWriteLock
  public void replaceChange(@NotNull final Side sourceSide) {
    Side affectedSide = sourceSide.other();
    if (!myViewer.isEditable(affectedSide, true)) return;

    final Document document1 = myViewer.getDocument(Side.LEFT);
    final Document document2 = myViewer.getDocument(Side.RIGHT);
    assert document1 != null && document2 != null;

    DiffUtil.applyModification(affectedSide.select(document1, document2),
                               affectedSide.getStartLine(myLineFragment), affectedSide.getEndLine(myLineFragment),
                               sourceSide.select(document1, document2),
                               sourceSide.getStartLine(myLineFragment), sourceSide.getEndLine(myLineFragment));

    destroyHighlighter();
  }

  //
  // Gutter
  //

  public void updateGutterActions() {
    for (MyGutterOperation operation : myOperations) {
      operation.update();
    }
  }

  @NotNull
  private MyGutterOperation createOperation(@NotNull Side side, boolean secondAction) {
    int line = secondAction ? Math.min(myLine1 + 1, myLine2 - 1) : myLine1;
    int offset = myEditor.getDocument().getLineStartOffset(line);

    RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                                 HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                 null,
                                                                                 HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(side, highlighter);
  }

  private class MyGutterOperation {
    @NotNull private final Side mySide;
    @NotNull private final RangeHighlighter myHighlighter;

    private MyGutterOperation(@NotNull Side sourceSide, @NotNull RangeHighlighter highlighter) {
      mySide = sourceSide;
      myHighlighter = highlighter;

      update();
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    public void update() {
      if (myHighlighter.isValid()) myHighlighter.setGutterIconRenderer(createRenderer());
    }

    @Nullable
    public GutterIconRenderer createRenderer() {
      if (!myViewer.isEditable(mySide.other(), true)) return null;
      boolean bothEditable = myViewer.isEditable(mySide, true);

      if (bothEditable) {
        if (mySide.isLeft()) {
          return createIconRenderer(mySide, "Apply Before", AllIcons.Diff.ArrowRight);
        }
        else {
          return createIconRenderer(mySide, "Apply After", AllIcons.Diff.Arrow);
        }
      }
      else {
        if (mySide.isLeft()) {
          return createIconRenderer(mySide, "Revert", AllIcons.Diff.Remove);
        }
        else {
          return createIconRenderer(mySide, "Apply", AllIcons.Diff.Arrow);
        }
      }
    }
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull final String tooltipText,
                                                @NotNull final Icon icon) {
    return new GutterIconRenderer() {
      @NotNull
      @Override
      public Icon getIcon() {
        return icon;
      }

      public boolean isNavigateAction() {
        return true;
      }

      @Nullable
      @Override
      public AnAction getClickAction() {
        return new DumbAwareAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            final Project project = e.getProject();
            final Document document = myViewer.getDocument(sourceSide.other());
            assert document != null;

            DiffUtil.executeWriteCommand(document, project, "Replace change", new Runnable() {
              @Override
              public void run() {
                replaceChange(sourceSide);
              }
            });
          }
        };
      }

      @Override
      public boolean equals(Object obj) {
        return obj == this;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Nullable
      @Override
      public String getTooltipText() {
        return tooltipText;
      }
    };
  }
}
