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

import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.util.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SimpleThreesideDiffChange extends ThreesideDiffChangeBase {
  @NotNull private final SimpleThreesideDiffViewer myViewer;
  @Nullable private final MergeInnerDifferences myInnerFragments;

  private final int[] myLineStarts = new int[3];
  private final int[] myLineEnds = new int[3];

  private boolean myIsValid = true;

  public SimpleThreesideDiffChange(@NotNull MergeLineFragment fragment,
                                   @NotNull MergeConflictType conflictType,
                                   @Nullable MergeInnerDifferences innerFragments,
                                   @NotNull SimpleThreesideDiffViewer viewer) {
    super(conflictType);
    myViewer = viewer;
    myInnerFragments = innerFragments;

    for (ThreeSide side : ThreeSide.values()) {
      myLineStarts[side.getIndex()] = fragment.getStartLine(side);
      myLineEnds[side.getIndex()] = fragment.getEndLine(side);
    }

    reinstallHighlighters();
  }

  @RequiresEdt
  public void reinstallHighlighters() {
    destroyHighlighters();
    installHighlighters();

    destroyInnerHighlighters();
    installInnerHighlighters();

    destroyOperations();
    installOperations();
  }

  @Override
  protected void installOperations() {
    myOperations.add(createAcceptOperation(ThreeSide.LEFT, ThreeSide.BASE));
    myOperations.add(createAcceptOperation(ThreeSide.RIGHT, ThreeSide.BASE));
    myOperations.add(createAcceptOperation(ThreeSide.BASE, ThreeSide.LEFT));
    myOperations.add(createAcceptOperation(ThreeSide.BASE, ThreeSide.RIGHT));
  }

  //
  // Getters
  //

  @Override
  public int getStartLine(@NotNull ThreeSide side) {
    return side.select(myLineStarts);
  }

  @Override
  public int getEndLine(@NotNull ThreeSide side) {
    return side.select(myLineEnds);
  }

  @Override
  public boolean isResolved(@NotNull ThreeSide side) {
    return false;
  }

  @NotNull
  @Override
  protected Editor getEditor(@NotNull ThreeSide side) {
    return myViewer.getEditor(side);
  }

  @Nullable
  @Override
  protected MergeInnerDifferences getInnerFragments() {
    return myInnerFragments;
  }

  public boolean isValid() {
    return myIsValid;
  }

  public void markInvalid() {
    myIsValid = false;

    destroyOperations();
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull ThreeSide side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);
    int sideIndex = side.getIndex();

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myLineStarts[sideIndex] = newRange.startLine;
    myLineEnds[sideIndex] = newRange.endLine;

    return newRange.damaged;
  }

  //
  // Modification
  //

  static @NotNull @Nls String getApplyActionText(@NotNull DiffViewerBase viewer,
                                                 @NotNull ThreeSide sourceSide,
                                                 @NotNull ThreeSide modifiedSide) {
    Key<@Nls String> key = null;
    if (sourceSide == ThreeSide.BASE && modifiedSide == ThreeSide.LEFT) {
      key = DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_LEFT_ACTION_TEXT;
    }
    else if (sourceSide == ThreeSide.BASE && modifiedSide == ThreeSide.RIGHT) {
      key = DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_RIGHT_ACTION_TEXT;
    }
    else if (sourceSide == ThreeSide.LEFT && modifiedSide == ThreeSide.BASE) {
      key = DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_TO_BASE_ACTION_TEXT;
    }
    else if (sourceSide == ThreeSide.RIGHT && modifiedSide == ThreeSide.BASE) {
      key = DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_TO_BASE_ACTION_TEXT;
    }
    if (key != null) {
      String customValue = DiffUtil.getUserData(viewer.getRequest(), viewer.getContext(), key);
      if (customValue != null) return customValue;
    }

    return DiffBundle.message("action.presentation.diff.accept.text");
  }

  @NotNull
  private DiffGutterOperation createAcceptOperation(@NotNull ThreeSide sourceSide, @NotNull ThreeSide modifiedSide) {
    EditorEx editor = myViewer.getEditor(sourceSide);
    int offset = DiffGutterOperation.lineToOffset(editor, getStartLine(sourceSide));

    return new DiffGutterOperation.Simple(editor, offset, () -> {
      boolean isOtherEditable = myViewer.isEditable(modifiedSide);
      if (!isOtherEditable) return null;

      boolean isChanged = sourceSide != ThreeSide.BASE && isChange(sourceSide) ||
                          modifiedSide != ThreeSide.BASE && isChange(modifiedSide);
      if (!isChanged) return null;

      String text = getApplyActionText(myViewer, sourceSide, modifiedSide);
      Side arrowDirection = Side.fromLeft(sourceSide == ThreeSide.LEFT ||
                                          modifiedSide == ThreeSide.RIGHT);
      Icon icon = DiffUtil.getArrowIcon(arrowDirection);

      return createIconRenderer(sourceSide, modifiedSide, text, icon,
                                () -> myViewer.replaceChange(this, sourceSide, modifiedSide));
    });
  }

  private GutterIconRenderer createIconRenderer(@NotNull final ThreeSide sourceSide,
                                                @NotNull final ThreeSide modifiedSide,
                                                @NotNull final @NlsContexts.Tooltip String tooltipText,
                                                @NotNull final Icon icon,
                                                @NotNull final Runnable perform) {
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        if (!isValid()) return;
        final Project project = myViewer.getProject();
        final Document document = myViewer.getEditor(modifiedSide).getDocument();
        DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.replace.change.command"), perform);
      }
    };
  }
}