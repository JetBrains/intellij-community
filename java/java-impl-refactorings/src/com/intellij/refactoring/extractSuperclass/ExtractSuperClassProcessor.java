// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

public class ExtractSuperClassProcessor extends ExtractSuperBaseProcessor {

  public ExtractSuperClassProcessor(Project project,
                                    PsiDirectory targetDirectory, String newClassName, PsiClass aClass, MemberInfo[] memberInfos, boolean replaceInstanceOf,
                                    DocCommentPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, targetDirectory, newClassName, aClass, memberInfos, javaDocPolicy);
  }


  @Override
  protected PsiClass extractSuper(final String superClassName) throws IncorrectOperationException {
    return ExtractSuperClassUtil.extractSuperClass(myProject, myClass.getContainingFile().getContainingDirectory(), superClassName, myClass, myMemberInfos, myJavaDocPolicy);
  }

  @Override
  protected boolean isSuperInheritor(PsiClass aClass) {
    if (!aClass.isInterface()) {
      return myClass.isInheritor(aClass, true);
    }
    else {
      return doesAnyExtractedInterfaceExtends(aClass);
    }
  }

  @Override
  protected boolean isInSuper(PsiElement member) {
    if (member instanceof PsiField field) {
      final PsiClass containingClass = field.getContainingClass();
      if (myClass.isInheritor(containingClass, true)) return true;
      return doMemberInfosContain(field);
    }
    else if (member instanceof PsiMethod method) {
      final PsiClass currentSuperClass = myClass.getSuperClass();
      if (currentSuperClass != null) {
        final PsiMethod methodBySignature = currentSuperClass.findMethodBySignature(method, true);
        if (methodBySignature != null) return true;
      }
      return doMemberInfosContain(method);
    }
    return false;
  }
}
