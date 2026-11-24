// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A legacy usage info of a move that describes its write as an edit on non-physical copies. The move on the ModPlan engine uses it.
 * <p>
 * The edits run on the copies before the files move. So the usage computes its new text on the original files, from the places of the
 * moved items after the move. {@code bindToElement} cannot do this, because it takes the current places of the files.
 * <pre>{@code
 * <!-- d/page.html -->
 * <img src="../a.png">
 * // move d/page.html into e/f
 * <img src="../../a.png">
 * }</pre>
 */
public interface ModPlanMoveUsage {
  /**
   * @return the edit; null if the edit cannot run on the copies, which makes the usage a
   * {@link com.intellij.refactoring.modplan.ModPlanConflictKind#BROKEN} conflict that the move does not change
   */
  @RequiresReadLock
  @Nullable ModPlanEdit asModPlanEdit(@NotNull ModPlanMovePlaces places);
}