// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.NotNull;

/**
 * View of document's range affected by some {@link AdjustableUndoableAction}.
 * <b>View</b> means that if the originating action is reordered with another one,
 * an instance of this view must return a new range's position via its getters.
 * For the sake of brevity, such a position can be denoted by
 * a triple of (offset, old length, new length)
 */
public interface ActionChangeRange {
  /**
   * Get start offset of affected range
   */
  int getOffset();

  /**
   * Get length of removed fragment
   */
  int getOldLength();

  /**
   * Get length of inserted fragment
   */
  int getNewLength();

  /**
   * Check whether this range still corresponds to any {@link AdjustableUndoableAction}.
   * Invalid ranges can be safely removed from the <b>bottom</b> of undo/redo stack
   * since they won't be taken into account by any valid range
   */
  boolean isValid();

  /**
   * Get a unique ID of this change range.
   * <b>Note:</b> zero ID is reserved and must be considered invalid.
   * The following equations must be satisfied:
   * <pre>
   * {@code
   * range.getId() == -range.asInverted().getId();
   * range.getId() == range.asInverted().asInverted().getId();
   * }
   * </pre>
   */
  int getId();

  /**
   * Get ID of the original change range this one was created from.
   * If this range is created immediately by some {@link AdjustableUndoableAction},
   * returns the same ID as {@link ActionChangeRange#getId()}.
   * No possible sequence of {@link ActionChangeRange#asInverted()} and
   * {@link ActionChangeRange#createIndependentCopy(boolean)} must change it,
   * i.e., the following equations must be satisfied:
   * <pre>
   * {@code
   * range.getId() == range.asInverted().getOriginatorId();
   * range.getId() == range.createIndependentCopy(true).getOriginatorId();
   * range.getId() == range.asInverted().createIndependentCopy(false).getOriginatorId();
   * }
   * </pre>
   */
  int getOriginatorId();

  /**
   * Create independent copy of this range. <b>Independent</b> means that if the original range is moved,
   * its copy won't change, the opposite is true as well
   * @param invalidate if <code>true</code>, obtained copy will be invalid right from the start
   *                   otherwise it shares validity with its originator
   */
  @NotNull ActionChangeRange createIndependentCopy(boolean invalidate);

  /**
   * Get another <b>view</b> which shows the same range but with its old and new lengths swapped
   */
  @NotNull ActionChangeRange asInverted();

  /**
   * Adjust this range as if the corresponding change was applied after another one (affecting the same document).
   * <b>Doesn't</b> change the range provided as an argument.
   * <br/>
   * This might not be immediately obvious but using this method together with {@link ActionChangeRange#asInverted()}
   * makes it possible to actually swap the order of two independent changes of the same document:
   * <pre>
   * {@code
   * firstRange.moveAfter(secondRange, true);
   * secondRange.moveAfter(firstRange.asInverted(), false);
   * }
   * </pre>
   * @param preferBefore In case if it's not obvious whether the other range is before or after this one,
   *                     assume it's before when <code>true</code> and after otherwise
   * @return <code>true</code> if it's possible to move this range and <code>false</code> otherwise
   */
  boolean moveAfter(@NotNull ActionChangeRange other, boolean preferBefore);
}
