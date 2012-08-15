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
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * @author dsl
 */
public class ElementNeedsThis extends ClassThisReferencesVisitor {
  private boolean myResult = false;
  private final PsiElement myMember;

  public ElementNeedsThis(PsiClass aClass, PsiElement member) {
    super(aClass);
    myMember = member;
  }

  public ElementNeedsThis(PsiClass aClass) {
    super(aClass);
    myMember = null;
  }
  public boolean usesMembers() {
    return myResult;
  }

  protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
    if (classMember == null || classMember.equals(myMember)) return;
    if (classMember.hasModifierProperty(PsiModifier.STATIC)) return;

    if (ignoreUsedTypeParams() && classMember instanceof PsiTypeParameter) return;
    myResult = true;
  }

  protected boolean ignoreUsedTypeParams() {
    return myMember != null;
  }

  protected void visitExplicitThis(PsiClass referencedClass, PsiThisExpression reference) {
    myResult = true;
  }

  protected void visitExplicitSuper(PsiClass referencedClass, PsiSuperExpression reference) {
    myResult = true;
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiClass aClass = PsiUtil.resolveClassInType(expression.getType());
    if (aClass instanceof PsiTypeParameter) {
      final PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
      if (owner instanceof PsiClass && myClassSuperClasses.contains(owner)) {
        myResult = true;
      }
    }
  }

  @Override public void visitElement(PsiElement element) {
    if (myResult) return;
    super.visitElement(element);
  }
}
