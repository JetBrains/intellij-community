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
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;

/**
* @author max
*/
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
