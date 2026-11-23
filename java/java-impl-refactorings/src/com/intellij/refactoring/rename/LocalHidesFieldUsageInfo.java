/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A reference to a field that a renamed local variable or parameter hides after the rename. The write qualifies the reference.
 *
 * <pre>{@code
 * class InnerClass {
 *   private int myInnerField;
 *   void m(int param) { int sum = param + myInnerField; }
 * }
 * // rename param to myInnerField
 * class InnerClass {
 *   private int myInnerField;
 *   void m(int myInnerField) { int sum = myInnerField + this.myInnerField; }
 * }
 * }</pre>
 */
class LocalHidesFieldUsageInfo extends ResolvableCollisionUsageInfo implements ModPlanWritableUsageInfo {
  LocalHidesFieldUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  /**
   * The reference gets the class of the field as the qualifier: {@code this}, {@code Outer.this}, or the class name for a static field.
   *
   * <pre>{@code
   * class RenameCollisions {
   *   private int myField = 7;
   *   class InnerClass { void m() { int localVar = 0; int sum = localVar + myField; } }
   * }
   * // rename localVar to myField
   * class RenameCollisions {
   *   private int myField = 7;
   *   class InnerClass { void m() { int myField = 0; int sum = myField + RenameCollisions.this.myField; } }
   * }
   *
   * class InnerClass {
   *   static final int INNER_STATIC_FIELD = 13;
   *   void m() { int localVar = 0; int sum = localVar + INNER_STATIC_FIELD; }
   * }
   * // rename localVar to INNER_STATIC_FIELD
   * class InnerClass {
   *   static final int INNER_STATIC_FIELD = 13;
   *   void m() { int INNER_STATIC_FIELD = 0; int sum = INNER_STATIC_FIELD + InnerClass.INNER_STATIC_FIELD; }
   * }
   * }</pre>
   */
  @Override
  public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
    if (!(getElement() instanceof PsiJavaCodeReferenceElement reference) || !(reference.resolve() instanceof PsiField field) ||
        field.getContainingClass() == null) {
      return List.of();
    }
    return List.of(ModPlanJavaRenameProvider.qualifiedMemberEdit(reference, newName, field.getContainingClass(),
                                                                 field.hasModifierProperty(PsiModifier.STATIC)));
  }
}
