// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractInterface;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

public class ExtractInterfaceProcessor extends ExtractSuperBaseProcessor {
  public ExtractInterfaceProcessor(Project project,
               boolean replaceInstanceOf,
               PsiDirectory targetDirectory,
               String newClassName,
               PsiClass aClass,
               MemberInfo[] memberInfos,
               DocCommentPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, targetDirectory, newClassName, aClass, memberInfos, javaDocPolicy);
  }

  @Override
  protected PsiClass extractSuper(String superClassName) throws IncorrectOperationException {
    return ExtractInterfaceHandler.extractInterface(myClass.getContainingFile().getContainingDirectory(), myClass, superClassName, myMemberInfos, myJavaDocPolicy);
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
      return doMemberInfosContain(field);
    }
    else if (member instanceof PsiMethod method) {
      return doMemberInfosContain(method);
    }
    return false;
  }
}
