// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * A legacy rename usage info that can describe its own write as edits on non-physical copies, for the rename on the ModPlan engine, such
 * as a reference that a collision makes qualified. A code usage that does not implement it is written as a reference, as
 * {@code RenameUtilBase.rename} writes it.
 * <p>
 * Whatever the write needs to know is resolved here, in the analysis: the edit runs on a copy, where a reference into another file sees
 * that file before the rename.
 */
public interface ModPlanWritableUsageInfo {
  /**
   * @param newName the new name of the renamed element that the usage belongs to
   * @param renamed every element of the rename with its new name
   * @return the edits; empty if the usage needs no write; null if this usage cannot be written on copies, which makes it a conflict of
   * the rename
   */
  @RequiresReadLock
  @Nullable @Unmodifiable List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed);
}
