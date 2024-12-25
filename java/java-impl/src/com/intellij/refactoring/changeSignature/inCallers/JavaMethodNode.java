// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaMethodNode extends JavaMemberNode<PsiMethod> {
  protected JavaMethodNode(PsiMethod method,
                           Set<PsiMethod> called,
                           Project project,
                           Runnable cancelCallback) {
    super(method, called, project, cancelCallback);
  }

  @Override
  protected MemberNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
    return new JavaMethodNode(caller, called, myProject, myCancelCallback);
  }

  @Override
  protected @Unmodifiable List<PsiMethod> computeCallers() {
    final PsiReference[] refs = MethodReferencesSearch.search(myMethod).toArray(PsiReference.EMPTY_ARRAY);

    List<PsiMethod> result = new ArrayList<>();
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (!(element instanceof PsiReferenceExpression refExpr) ||
          !(refExpr.getQualifierExpression() instanceof PsiSuperExpression)) {
        final PsiElement enclosingContext = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
        if (enclosingContext instanceof PsiMethod enclosingMethod && !result.contains(enclosingContext) &&
            !getMember().equals(enclosingContext) && !myCalled.contains(getMember()) &&  //do not add recursive methods
            noLibraryInheritors(enclosingMethod)) {
          result.add(enclosingMethod);
        }
        else if (element instanceof PsiClass aClass) {
          final PsiMethod method = JavaPsiFacade.getElementFactory(myProject).createMethodFromText(aClass.getName() + "(){}", aClass);
          if (!result.contains(method)) {
            result.add(method);
          }
        }
      }
    }
    return result;
  }

  private static boolean noLibraryInheritors(PsiMethod enclosingContext) {
    PsiMethod[] superMethods = enclosingContext.findDeepestSuperMethods();
    return superMethods.length == 0 || ContainerUtil.exists(superMethods, method -> !method.isWritable());
  }
}
