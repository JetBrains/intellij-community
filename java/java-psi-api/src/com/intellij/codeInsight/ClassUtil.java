// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClassUtil {
  private ClassUtil() { }

  /**
   * Returns the first abstract method found in the specified class. The class itself is looked in first,
   * then the superclasses are checked.
   * @param aClass  the class to find abstract methods in.
   * @return null, if no abstract methods were found, an abstract method otherwise.
   */
  @Nullable
  public static PsiMethod getAnyAbstractMethod(@NotNull PsiClass aClass) {
    for (PsiMethod method : aClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }
    return getAnyMethodToImplement(aClass);
  }

  @Nullable
  public static PsiMethod getAnyMethodToImplement(@NotNull PsiClass aClass) {
    final PsiClass superClass = aClass instanceof PsiAnonymousClass ? PsiUtil.resolveClassInClassTypeOnly(((PsiAnonymousClass)aClass).getBaseClassType()) : aClass.getSuperClass();
    if (superClass != null && !superClass.hasModifierProperty(PsiModifier.ABSTRACT) && !superClass.isEnum() && aClass.getImplementsListTypes().length == 0) {
      return null;
    }
    Set<PsiMethod> alreadyImplemented = new HashSet<>();
    for (HierarchicalMethodSignature signatureHierarchical : aClass.getVisibleSignatures()) {
      for (PsiMethod superS : signatureHierarchical.getMethod().findSuperMethods()) {
        add(superS, alreadyImplemented);
      }
    }
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (HierarchicalMethodSignature signatureHierarchical : aClass.getVisibleSignatures()) {
      PsiMethod method = signatureHierarchical.getMethod();
      PsiClass containingClass = method.getContainingClass();
      // TODO: remove record check when we provide synthetic equals/hashCode/toString for Record classes
      if (containingClass == null || CommonClassNames.JAVA_LANG_RECORD.equals(containingClass.getQualifiedName())) {
        continue;
      }
      if (!aClass.equals(containingClass)
          && method.hasModifierProperty(PsiModifier.ABSTRACT)
          && !method.hasModifierProperty(PsiModifier.STATIC)
          && !method.hasModifierProperty(PsiModifier.PRIVATE)
          && !alreadyImplemented.contains(method)) {
        return method;
      }
      final List<HierarchicalMethodSignature> superSignatures = new ArrayList<>(signatureHierarchical.getInaccessibleSuperSignatures());
      superSignatures.addAll(signatureHierarchical.getSuperSignatures());
      for (HierarchicalMethodSignature superSignatureHierarchical : superSignatures) {
        final PsiMethod superMethod = superSignatureHierarchical.getMethod();
        if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT) && !resolveHelper.isAccessible(superMethod, method, null)) {
          return superMethod;
        }
      }
    }

    return null;
  }

  private static boolean add(PsiMethod method, @NotNull Set<? super PsiMethod> alreadyImplemented) {
    boolean already = alreadyImplemented.add(method);
    if (!already) return false;

    for (PsiMethod superSig : method.findSuperMethods()) {
      already &= add(superSig, alreadyImplemented);
    }
    return already;
  }
}
