// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;

public class DependentMembersCollector extends DependentMembersCollectorBase<PsiMember, PsiClass> {
  public DependentMembersCollector(PsiClass clazz, PsiClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(PsiMember member) {
    member.accept(getVisitor());
  }

  private PsiElementVisitor getVisitor() {
    return new ClassMemberReferencesVisitor(getClazz()) {
      @Override
      protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
        if (!existsInSuperClass(classMember)) {
          myCollection.add(classMember);
        }
      }
    };
  }

  private boolean existsInSuperClass(PsiMember classMember) {
    if (getSuperClass() == null) return false;
    if (!(classMember instanceof PsiMethod)) return false;
    final PsiMethod method = ((PsiMethod)classMember);
    final PsiMethod methodBySignature = (getSuperClass()).findMethodBySignature(method, true);
    return methodBySignature != null;
  }
}
