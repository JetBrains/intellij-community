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


import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A reference to a member of an outer class that a renamed member of an inner class hides after the rename. The write qualifies the
 * reference.
 *
 * <pre>{@code
 * class RenameCollisions {
 *   private int myField = 7;
 *   private class InnerClass {
 *     private int myInnerField = 15;
 *     int sum() { return myInnerField + myField; }
 *   }
 * }
 * // rename myInnerField to myField
 * class RenameCollisions {
 *   private int myField = 7;
 *   private class InnerClass {
 *     private int myField = 15;
 *     int sum() { return myField + RenameCollisions.this.myField; }
 *   }
 * }
 * }</pre>
 */
class MemberHidesOuterMemberUsageInfo extends ResolvableCollisionUsageInfo implements ModPlanWritableUsageInfo {
  MemberHidesOuterMemberUsageInfo(PsiElement ref, PsiMember member) {
    super(ref, member);
  }

  /**
   * The reference keeps the outer member. For a renamed field or method it gets the qualifier of the outer member, as in the example of
   * the class. For a renamed class it gets the qualified name of the class that it refers to.
   *
   * <pre>{@code
   * class A { class B { int x = 42; } }
   * class D {
   *   static class C extends A {
   *     int x = 23;
   *     int m() { return C.this.x; }
   *   }
   * }
   * // rename A.B to C
   * class A { class C { int x = 42; } }
   * class D {
   *   static class C extends A {
   *     int x = 23;
   *     int m() { return D.C.this.x; }
   *   }
   * }
   * }</pre>
   */
  @Override
  public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
    PsiElement occurrence = getElement();
    PsiReference reference = occurrence == null ? null : occurrence.getReference();
    if (reference == null || !(reference.resolve() instanceof PsiMember member)) return List.of();
    if (!(getReferencedElement() instanceof PsiClass)) {
      return List.of(ModPlanEdit.update(occurrence, copy -> RenameJavaMemberProcessor.qualifyOuterMemberReferences(
        List.of(new MemberHidesOuterMemberUsageInfo(copy, member)))));
    }
    // As the legacy rename of a class: the reference to the outer member gets its class as the qualifier.
    if (!(occurrence instanceof PsiJavaCodeReferenceElement) || member.getName() == null || member.getContainingClass() == null) {
      return List.of();
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(occurrence.getProject());
    PsiJavaCodeReferenceElement qualified = factory.createReferenceFromText("A." + member.getName(), occurrence);
    Objects.requireNonNull(qualified.getQualifier()).replace(factory.createClassReferenceElement(member.getContainingClass()));
    String text = qualified.getText();
    return List.of(ModPlanEdit.update(occurrence, copy -> {
      copy.replace(JavaPsiFacade.getElementFactory(copy.getProject()).createReferenceFromText(text, copy));
    }));
  }
}