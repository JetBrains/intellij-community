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
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.chains.*;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.DiffFragmentWithLink;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.impl.DiffWindow;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleDiffChange {
  private static final Logger LOG = Logger.getInstance(SimpleDiffChange.class);

  @NotNull private final SimpleDiffViewer myViewer;

  @NotNull private final LineFragment myFragment;
  @Nullable private final List<DiffFragment> myInnerFragments;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<>();

  private boolean myIsValid = true;
  private int[] myLineStartShifts = new int[2];
  private int[] myLineEndShifts = new int[2];

  // TODO: adjust color from inner fragments - configurable
  public SimpleDiffChange(@NotNull SimpleDiffViewer viewer,
                          @NotNull LineFragment fragment) {
    myViewer = viewer;

    myFragment = fragment;
    myInnerFragments = fragment.getInnerFragments();

    installHighlighter();
  }

  public void installHighlighter() {
    assert myHighlighters.isEmpty();

    if (myInnerFragments != null) {
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

    for (MyGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  private void doInstallHighlighterSimple() {
    createHighlighter(Side.LEFT, false);
    createHighlighter(Side.RIGHT, false);
  }

  private void doInstallHighlighterWithInner() {
    assert myInnerFragments != null;
    boolean ignored = !ContainerUtil.exists(myInnerFragments, innerFragment -> innerFragment instanceof DiffFragmentWithLink);

    createHighlighter(Side.LEFT, ignored);
    createHighlighter(Side.RIGHT, ignored);

    for (DiffFragment fragment : myInnerFragments) {
      createInlineHighlighter(fragment, Side.LEFT);
      createInlineHighlighter(fragment, Side.RIGHT);
    }
  }

  private void doInstallActionHighlighters() {
    myOperations.add(createOperation(Side.LEFT));
    myOperations.add(createOperation(Side.RIGHT));
    myOperations.addAll(createShowMatchingFragmentOperation(Side.LEFT));
    myOperations.addAll(createShowMatchingFragmentOperation(Side.RIGHT));
  }

  private void createHighlighter(@NotNull Side side, boolean ignored) {
    Editor editor = myViewer.getEditor(side);

    TextDiffType type = DiffUtil.getLineDiffType(myFragment);
    int startLine = side.getStartLine(myFragment);
    int endLine = side.getEndLine(myFragment);

    myHighlighters.addAll(DiffDrawUtil.createHighlighter(editor, startLine, endLine, type, ignored));
    myHighlighters.addAll(DiffDrawUtil.createLineMarker(editor, startLine, endLine, type, false));
  }

  private void createInlineHighlighter(@NotNull DiffFragment fragment, @NotNull Side side) {
    int start = side.getStartOffset(fragment);
    int end = side.getEndOffset(fragment);
    TextDiffType type = DiffUtil.getDiffType(fragment);

    int startOffset = side.getStartOffset(myFragment);
    start += startOffset;
    end += startOffset;

    Editor editor = myViewer.getEditor(side);
    myHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, start, end, type, fragment instanceof DiffFragmentWithLink));
  }

  public void updateGutterActions(boolean force) {
    for (MyGutterOperation operation : myOperations) {
      operation.update(force);
    }
  }

  //
  // Getters
  //

  public int getStartLine(@NotNull Side side) {
    return side.getStartLine(myFragment) + side.select(myLineStartShifts);
  }

  public int getEndLine(@NotNull Side side) {
    return side.getEndLine(myFragment) + side.select(myLineEndShifts);
  }

  @NotNull
  public TextDiffType getDiffType() {
    return DiffUtil.getLineDiffType(myFragment);
  }

  public boolean isValid() {
    return myIsValid;
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull Side side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);
    int sideIndex = side.getIndex();

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myLineStartShifts[sideIndex] += newRange.startLine - line1;
    myLineEndShifts[sideIndex] += newRange.endLine - line2;

    if (newRange.damaged) {
      for (MyGutterOperation operation : myOperations) {
        operation.dispose();
      }
      myOperations.clear();

      myIsValid = false;
    }

    return newRange.damaged;
  }

  //
  // Change applying
  //

  public boolean isSelectedByLine(int line, @NotNull Side side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);

    return DiffUtil.isSelectedByLine(line, line1, line2);
  }

  //
  // Helpers
  //

  @NotNull
  private MyGutterOperation createOperation(@NotNull Side side) {
    int offset = side.getStartOffset(myFragment);
    EditorEx editor = myViewer.getEditor(side);
    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                               HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                               null,
                                                                               HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(side, highlighter, null);
  }

  @NotNull
  private List<MyGutterOperation> createShowMatchingFragmentOperation(@NotNull Side side) {
    List<MyGutterOperation> operations = new ArrayList<>();

    if (myInnerFragments != null) {
      for (DiffFragment fragment : myInnerFragments) {
        if (fragment instanceof DiffFragmentWithLink) {
          int offset = side.getStartOffset(myFragment) + side.getStartOffset(fragment);
          EditorEx editor = myViewer.getEditor(side);
          RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                                     HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                     null,
                                                                                     HighlighterTargetArea.LINES_IN_RANGE);
          if (side.getEndOffset(fragment) > side.getStartOffset(fragment))
            operations.add(new MyGutterOperation(side, highlighter, (DiffFragmentWithLink)fragment));
        }
      }
    }

    return operations;
  }

  private class MyGutterOperation {
    @NotNull private final Side mySide;
    @NotNull private final RangeHighlighter myHighlighter;
    @Nullable private final DiffFragmentWithLink myInnerFragment;

    private boolean myCtrlPressed;

    private MyGutterOperation(@NotNull Side side, @NotNull RangeHighlighter highlighter, @Nullable DiffFragmentWithLink innerFragment) {
      mySide = side;
      myHighlighter = highlighter;
      myInnerFragment = innerFragment;

      update(true);
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    public void update(boolean force) {
      if (!force && !areModifiersChanged()) {
        return;
      }
      if (myHighlighter.isValid()) myHighlighter.setGutterIconRenderer(createRenderer());
    }

    private boolean areModifiersChanged() {
      return myCtrlPressed != myViewer.getModifierProvider().isCtrlPressed();
    }

    @Nullable
    public GutterIconRenderer createRenderer() {
      myCtrlPressed = myViewer.getModifierProvider().isCtrlPressed();

      boolean isOtherEditable = DiffUtil.isEditable(myViewer.getEditor(mySide.other()));
      boolean isAppendable = myFragment.getStartLine1() != myFragment.getEndLine1() &&
                             myFragment.getStartLine2() != myFragment.getEndLine2();

      if (isOtherEditable) {
        if (myCtrlPressed && isAppendable) {
          return createAppendRenderer(mySide);
        }
        else {
          return createApplyRenderer(mySide);
        }
      }
      else if (myInnerFragment != null)
        return createShowMatchingRenderer(myInnerFragment, mySide);

      return null;
    }
  }

  @Nullable
  private GutterIconRenderer createApplyRenderer(@NotNull final Side side) {
    return createIconRenderer(side, "Accept", DiffUtil.getArrowIcon(side), () -> {
      myViewer.replaceChange(this, side);
    });
  }

  @Nullable
  private GutterIconRenderer createAppendRenderer(@NotNull final Side side) {
    return createIconRenderer(side, "Append", DiffUtil.getArrowDownIcon(side), () -> {
      UsageTrigger.trigger("diff.SimpleDiffChange.Append");
      myViewer.appendChange(this, side);
    });
  }

  @Nullable
  private GutterIconRenderer createShowMatchingRenderer(@NotNull DiffFragmentWithLink innerFragment, @NotNull final Side side) {
    return new DiffGutterRenderer(AllIcons.Actions.Preview, new File(innerFragment.getFile()).getName() + ":" + innerFragment.getOffsetInFile()) {
      @Override
      protected void performAction(AnActionEvent event) {
        Project project = event.getProject();
        if (myViewer.getRequest() instanceof SimpleDiffRequest && project != null) {
          DiffRequestChain requestsChain = ((SimpleDiffRequest)myViewer.getRequest()).getRequestsChain();
          List<? extends DiffRequestProducer> requests = requestsChain != null ? requestsChain.getRequests() : Collections.emptyList();

          for (DiffRequestProducer producer : requests) {
            if (producer.getName().endsWith(innerFragment.getFile())) {
              try {
                DiffRequest request = producer.process(project, new EmptyProgressIndicator());
                request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(side.other(), innerFragment.getOffsetInFile()));
                new DiffWindow(project, new SimpleDiffRequestChain(request), DiffDialogHints.DEFAULT).show();
                return;
              }
              catch (DiffRequestProducerException e) {
                LOG.warn(e);
              }
            }
          }
        }
      }
    };
  }

  @Nullable
  private GutterIconRenderer createIconRenderer(@NotNull final Side sourceSide,
                                                @NotNull final String tooltipText,
                                                @NotNull final Icon icon,
                                                @NotNull final Runnable perform) {
    if (!DiffUtil.isEditable(myViewer.getEditor(sourceSide.other()))) return null;
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void performAction(AnActionEvent e) {
        if (!myIsValid) return;
        final Project project = e.getProject();
        final Document document = myViewer.getEditor(sourceSide.other()).getDocument();
        DiffUtil.executeWriteCommand(document, project, "Replace change", perform);
      }
    };
  }
}
