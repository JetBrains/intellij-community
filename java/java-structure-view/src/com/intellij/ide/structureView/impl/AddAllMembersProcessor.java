// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @deprecated use conflict-filter processor with duplicates resolver {@link com.intellij.psi.scope.processor.ConflictFilterProcessor}
 */
public class AddAllMembersProcessor implements PsiScopeProcessor {
  private final Collection<PsiElement> myAllMembers;
  private final PsiClass myPsiClass;
  private final Map<MethodSignature,PsiMethod> myMethodsBySignature = new HashMap<>();

  public AddAllMembersProcessor(@NotNull Collection<PsiElement> allMembers, @NotNull PsiClass psiClass) {
    for (PsiElement psiElement : allMembers) {
      if (psiElement instanceof PsiMethod) mapMethodBySignature((PsiMethod)psiElement);
    }
    myAllMembers = allMembers;
    myPsiClass = psiClass;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    PsiMember member = (PsiMember)element;
    if (!isInteresting(element)) return true;
    if (myPsiClass.isInterface() && isObjectMember(element)) return true;
    if (!myAllMembers.contains(member) && isVisible(member, myPsiClass)) {
      if (member instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)member;
        if (shouldAdd(psiMethod)) {
          mapMethodBySignature(psiMethod);
          myAllMembers.add(PsiImplUtil.handleMirror(psiMethod));
        }
      }
      else {
        myAllMembers.add(PsiImplUtil.handleMirror(member));
      }
    }
    return true;
  }

  private static boolean isObjectMember(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
    if (containingClass == null) {
      return false;
    }
    else {
      final String qualifiedName = containingClass.getQualifiedName();
      return qualifiedName != null && qualifiedName.equals(Object.class.getName());
    }
  }

  private void mapMethodBySignature(PsiMethod psiMethod) {
    myMethodsBySignature.put(psiMethod.getSignature(PsiSubstitutor.EMPTY), psiMethod);
  }

  private boolean shouldAdd(PsiMethod psiMethod) {
    MethodSignature signature = psiMethod.getSignature(PsiSubstitutor.EMPTY);
    PsiMethod previousMethod = myMethodsBySignature.get(signature);
    if (previousMethod == null) return true;
    if (isInheritor(psiMethod, previousMethod)) {
      myAllMembers.remove(previousMethod);
      return true;
    }
    return false;
  }

  private static boolean isInteresting(PsiElement element) {
    return element instanceof PsiMethod
           || element instanceof PsiField
           || element instanceof PsiClass
           || element instanceof PsiClassInitializer
      ;
  }

  public static boolean isInheritor(PsiMethod method, PsiMethod baseMethod) {
    return !isStatic(method) && !isStatic(baseMethod) && method.getContainingClass().isInheritor(baseMethod.getContainingClass(), true);
  }

  private static boolean isStatic(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  private boolean isVisible(@NotNull PsiMember element, PsiClass psiClass) {
    return !isInheritedConstructor(element, psiClass) && PsiUtil.isAccessible(element, psiClass, null);
  }

  private static boolean isInheritedConstructor(PsiMember member, PsiClass psiClass) {
    if (!(member instanceof PsiMethod))
      return false;
    PsiMethod method = (PsiMethod)member;
    return method.isConstructor() && method.getContainingClass() != psiClass;
  }



}
