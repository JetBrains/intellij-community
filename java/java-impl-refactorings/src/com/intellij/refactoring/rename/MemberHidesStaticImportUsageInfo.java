// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import com.intellij.refactoring.util.RelatedUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;


/**
 * A reference that a static import gives to a member of another class. A renamed member takes the name of that member and hides the
 * reference after the rename.
 *
 * <pre>{@code
 * import static java.lang.String.valueOf;
 * class RenameCollisions {
 *   static class StaticInnerClass {
 *     static void siMethod() {}
 *     static void staticContext() { valueOf(0); }
 *   }
 * }
 * // rename siMethod to valueOf
 * import static java.lang.String.valueOf;
 * class RenameCollisions {
 *   static class StaticInnerClass {
 *     static void valueOf() {}
 *     static void staticContext() { String.valueOf(0); }
 *   }
 * }
 * }</pre>
 */
public class MemberHidesStaticImportUsageInfo extends RelatedUsageInfo implements ModPlanWritableUsageInfo {
  public MemberHidesStaticImportUsageInfo(PsiElement element, PsiElement referencedElement, PsiElement collidingElement) {
    super(element, null, referencedElement, collidingElement);
  }

  /**
   * The reference gets the class of the member it refers to as the qualifier, as {@code String.valueOf(0)} in the example of the class.
   * That member may be renamed too, and then the edit writes its new name.
   */
  @Override
  public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
    PsiReference reference = getReference();
    if (reference == null || !(getReferencedElement() instanceof PsiMember target) || target.getContainingClass() == null) return List.of();
    String name = renamed.getOrDefault(target, target.getName());
    if (name == null) return List.of();
    return List.of(ModPlanJavaRenameProvider.qualifiedMemberEdit(reference.getElement(), name, target.getContainingClass(), true));
  }
}
