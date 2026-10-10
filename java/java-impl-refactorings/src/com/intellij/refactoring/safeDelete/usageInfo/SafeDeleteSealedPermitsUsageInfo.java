// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.usageView.UsageInfo;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Adds the classes that take the place of a deleted class to the permits list of a sealed class, for the headless safe delete only.
 * <p>
 * The legacy {@link SafeDeleteExtendsClassUsageInfo#deleteElement()} writes this list while it moves the supertypes of the deleted class
 * to its heir, and the legacy usage that removes the deleted class from the same list is another write: the write that comes first
 * decides whether the list is left with a class, so the result depends on the order of the files. The headless writes are separate
 * edits: the removals first, because their elements lie inside the sealed class and the engine writes the last position first, then the
 * additions, which also restore {@code sealed} if the removal dropped it as the last entry.
 */
public final class SafeDeleteSealedPermitsUsageInfo extends UsageInfo implements SafeDeleteCustomUsageInfo, ModPlanSafeDeleteUsage {
  private final Collection<String> myNames;

  private SafeDeleteSealedPermitsUsageInfo(@NotNull PsiClass sealedClass, @NotNull Collection<String> names) {
    super(sealedClass);
    myNames = names;
  }

  /**
   * @return the usages and, for every sealed class that a heir of a deleted class gets as a supertype, a usage that adds the heir to
   * its permits list
   */
  public static @NotNull List<UsageInfo> withAdditions(@NotNull List<UsageInfo> usages) {
    Map<PsiClass, Collection<String>> permitsAdditions = new LinkedHashMap<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof SafeDeleteExtendsClassUsageInfo extending && extending.isSafeDelete()) {
        extending.sealedPermitsAdditions().forEach(
          (sealedClass, names) -> permitsAdditions.computeIfAbsent(sealedClass, _ -> new LinkedHashSet<>()).addAll(names));
      }
    }
    if (permitsAdditions.isEmpty()) return usages;
    List<UsageInfo> resultUsages = new ArrayList<>(usages);
    permitsAdditions.forEach((sealedClass, names) -> resultUsages.add(new SafeDeleteSealedPermitsUsageInfo(sealedClass, names)));
    return resultUsages;
  }

  @Override
  public void performRefactoring() {
    if (getElement() instanceof PsiClass sealedClass) write(sealedClass, myNames);
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    if (!(getElement() instanceof PsiClass sealedClass) || !SafeDeleteExtendsClassUsageInfo.isPhysicalJava(sealedClass)) return null;
    Collection<String> names = myNames;
    return ModPlanEdit.update(sealedClass, copy -> write(copy, names));
  }

  private static void write(@NotNull PsiClass sealedClass, @NotNull Collection<String> names) {
    SealedUtils.fillPermitsList(sealedClass, names);
    PsiModifierList modifiers = sealedClass.getModifierList();
    if (modifiers != null) modifiers.setModifierProperty(PsiModifier.SEALED, true);
  }
}
