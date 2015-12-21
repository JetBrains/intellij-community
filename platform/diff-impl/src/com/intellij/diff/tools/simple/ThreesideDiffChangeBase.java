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

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.TextDiffType;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ThreesideDiffChangeBase {
  @NotNull private ConflictType myType;

  public ThreesideDiffChangeBase(@NotNull MergeLineFragment fragment,
                                 @NotNull List<? extends EditorEx> editors,
                                 @NotNull ComparisonPolicy policy) {
    List<Document> documents = ContainerUtil.map(editors, new Function<EditorEx, Document>() {
      @Override
      public Document fun(EditorEx editorEx) {
        return editorEx.getDocument();
      }
    });
    myType = calcType(fragment, documents, policy);
  }

  //
  // Getters
  //

  public abstract int getStartLine(@NotNull ThreeSide side);

  public abstract int getEndLine(@NotNull ThreeSide side);

  @NotNull
  public TextDiffType getDiffType() {
    return myType.getDiffType();
  }

  @NotNull
  public ConflictType getType() {
    return myType;
  }

  public boolean isConflict() {
    return getDiffType() == TextDiffType.CONFLICT;
  }

  public boolean isChange(@NotNull Side side) {
    return myType.isChange(side);
  }

  public boolean isChange(@NotNull ThreeSide side) {
    switch (side) {
      case LEFT:
        return isChange(Side.LEFT);
      case BASE:
        return true;
      case RIGHT:
        return isChange(Side.RIGHT);
      default:
        throw new IllegalArgumentException(side.toString());
    }
  }

  //
  // Type
  //

  @NotNull
  public static ConflictType calcType(@NotNull MergeLineFragment fragment,
                                      @NotNull List<? extends Document> documents,
                                      @NotNull ComparisonPolicy policy) {
    boolean isLeftEmpty = isIntervalEmpty(fragment, ThreeSide.LEFT);
    boolean isBaseEmpty = isIntervalEmpty(fragment, ThreeSide.BASE);
    boolean isRightEmpty = isIntervalEmpty(fragment, ThreeSide.RIGHT);
    assert !isLeftEmpty || !isBaseEmpty || !isRightEmpty;

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return new ConflictType(TextDiffType.INSERTED, false, true);
      }
      else if (isRightEmpty) { // =--
        return new ConflictType(TextDiffType.INSERTED, true, false);
      }
      else { // =-=
        boolean equalModifications = compareContents(fragment, documents, policy, ThreeSide.LEFT, ThreeSide.RIGHT);
        return new ConflictType(equalModifications ? TextDiffType.INSERTED : TextDiffType.CONFLICT);
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return new ConflictType(TextDiffType.DELETED);
      }
      else { // -==, ==-, ===
        boolean unchangedLeft = compareContents(fragment, documents, policy, ThreeSide.BASE, ThreeSide.LEFT);
        boolean unchangedRight = compareContents(fragment, documents, policy, ThreeSide.BASE, ThreeSide.RIGHT);
        assert !unchangedLeft || !unchangedRight;

        if (unchangedLeft) return new ConflictType(isRightEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, false, true);
        if (unchangedRight) return new ConflictType(isLeftEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, true, false);

        boolean equalModifications = compareContents(fragment, documents, policy, ThreeSide.LEFT, ThreeSide.RIGHT);
        return new ConflictType(equalModifications ? TextDiffType.MODIFIED : TextDiffType.CONFLICT);
      }
    }
  }

  private static boolean compareContents(@NotNull MergeLineFragment fragment,
                                         @NotNull List<? extends Document> documents,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ThreeSide side1,
                                         @NotNull ThreeSide side2) {
    int start1 = fragment.getStartLine(side1);
    int end1 = fragment.getEndLine(side1);
    int start2 = fragment.getStartLine(side2);
    int end2 = fragment.getEndLine(side2);

    if (end2 - start2 != end1 - start1) return false;

    Document document1 = side1.select(documents);
    Document document2 = side2.select(documents);

    for (int i = 0; i < end1 - start1; i++) {
      int line1 = start1 + i;
      int line2 = start2 + i;

      CharSequence content1 = DiffUtil.getLinesContent(document1, line1, line1 + 1);
      CharSequence content2 = DiffUtil.getLinesContent(document2, line2, line2 + 1);
      if (!ComparisonManager.getInstance().isEquals(content1, content2, policy)) return false;
    }

    return true;
  }

  private static boolean isIntervalEmpty(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartLine(side) == fragment.getEndLine(side);
  }

  //
  // Helpers
  //

  public static class ConflictType {
    @NotNull private final TextDiffType myType;
    private final boolean myLeftChange;
    private final boolean myRightChange;

    public ConflictType(@NotNull TextDiffType type) {
      this(type, true, true);
    }

    public ConflictType(@NotNull TextDiffType type, boolean leftChange, boolean rightChange) {
      myType = type;
      myLeftChange = leftChange;
      myRightChange = rightChange;
    }

    @NotNull
    public TextDiffType getDiffType() {
      return myType;
    }

    public boolean isLeftChange() {
      return myLeftChange;
    }

    public boolean isRightChange() {
      return myRightChange;
    }

    public boolean isChange(@NotNull Side side) {
      return side.isLeft() ? myLeftChange : myRightChange;
    }
  }
}