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

  public void collect(PsiMember member) {
    member.accept(getVisitor());
  }

  private PsiElementVisitor getVisitor() {
    return new ClassMemberReferencesVisitor(getClazz()) {
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
