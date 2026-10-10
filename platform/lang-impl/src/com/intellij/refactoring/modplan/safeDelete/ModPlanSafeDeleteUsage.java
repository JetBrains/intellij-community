// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.Nullable;

/**
 * A legacy usage info that can describe its own write as an edit on non-physical copies, for the safe delete on the ModPlan engine.
 * <p>
 * A subclass that overrides the write ({@code performRefactoring}, and {@code deleteElement} for a
 * {@link com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo}) overrides this method too, or returns null from it.
 * <p>
 * If the whole write deletes one element, return {@link ModPlanEdit#delete} of it. The files are exact anyway:
 * {@link com.intellij.refactoring.modplan.ModPlanExecution#getAffectedFiles()} comes from the command.
 */
public interface ModPlanSafeDeleteUsage {
  /**
   * The analysis calls it only for a usage that does not block the deletion. A
   * {@link com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo} still checks {@code isSafeDelete()} and returns
   * null if it is not safe: this guards a caller that does not filter.
   *
   * @return the edit; null if this usage cannot be written on copies, which makes it a conflict of the deletion: the write leaves it
   */
  @RequiresReadLock
  @Nullable ModPlanEdit asModPlanEdit();
}
