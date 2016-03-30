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

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimpleThreesideDiffChange extends ThreesideDiffChangeBase {
  @NotNull private final List<? extends EditorEx> myEditors;
  @NotNull private final MergeLineFragment myFragment;

  private int[] myLineStartShifts = new int[3];
  private int[] myLineEndShifts = new int[3];

  public SimpleThreesideDiffChange(@NotNull MergeLineFragment fragment,
                                   @NotNull List<? extends EditorEx> editors,
                                   @NotNull ComparisonPolicy policy) {
    super(fragment, editors, policy);
    myEditors = editors;
    myFragment = fragment;

    reinstallHighlighters();
  }

  @CalledInAwt
  public void destroy() {
    destroyHighlighters();
    destroyInnerHighlighters();
  }

  @CalledInAwt
  public void reinstallHighlighters() {
    destroyHighlighters();
    installHighlighters();

    destroyInnerHighlighters();
    installInnerHighlighters();
  }

  //
  // Getters
  //

  @Override
  public int getStartLine(@NotNull ThreeSide side) {
    return myFragment.getStartLine(side) + side.select(myLineStartShifts);
  }

  @Override
  public int getEndLine(@NotNull ThreeSide side) {
    return myFragment.getEndLine(side) + side.select(myLineEndShifts);
  }

  @Override
  public boolean isResolved(@NotNull ThreeSide side) {
    return false;
  }

  @NotNull
  @Override
  protected Editor getEditor(@NotNull ThreeSide side) {
    return side.select(myEditors);
  }

  @Nullable
  @Override
  protected List<MergeWordFragment> getInnerFragments() {
    return myFragment.getInnerFragments();
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift, @NotNull ThreeSide side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);
    int sideIndex = side.getIndex();

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myLineStartShifts[sideIndex] += newRange.startLine - line1;
    myLineEndShifts[sideIndex] += newRange.endLine - line2;

    return newRange.damaged;
  }
}