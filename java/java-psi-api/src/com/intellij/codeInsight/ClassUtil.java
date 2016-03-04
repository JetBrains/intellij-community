/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.codeInsight;

import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ClassUtil {
  private ClassUtil() { }

  @Nullable
  public static PsiMethod getAnyAbstractMethod(@NotNull PsiClass aClass) {
    PsiMethod methodToImplement = getAnyMethodToImplement(aClass);
    if (methodToImplement != null) {
      return methodToImplement;
    }
    PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    return null;
  }

  @Nullable
  public static PsiMethod getAnyMethodToImplement(@NotNull PsiClass aClass) {
    Set<PsiMethod> alreadyImplemented = new THashSet<PsiMethod>();
    for (HierarchicalMethodSignature signatureHierarchical : aClass.getVisibleSignatures()) {
      for (PsiMethod superS : signatureHierarchical.getMethod().findSuperMethods()) {
        add(superS, alreadyImplemented);
      }
    }
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (HierarchicalMethodSignature signatureHierarchical : aClass.getVisibleSignatures()) {
      PsiMethod method = signatureHierarchical.getMethod();
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        continue;
      }
      if (!aClass.equals(containingClass)
          && method.hasModifierProperty(PsiModifier.ABSTRACT)
          && !method.hasModifierProperty(PsiModifier.STATIC)
          && !method.hasModifierProperty(PsiModifier.PRIVATE)
          && !alreadyImplemented.contains(method)) {
        return method;
      }
      final List<HierarchicalMethodSignature> superSignatures = signatureHierarchical.getSuperSignatures();
      for (HierarchicalMethodSignature superSignatureHierarchical : superSignatures) {
        final PsiMethod superMethod = superSignatureHierarchical.getMethod();
        if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT) && !resolveHelper.isAccessible(superMethod, method, null)) {
          return superMethod;
        }
      }
    }

    return checkPackageLocalInSuperClass(aClass);
  }

  @Nullable
  private static PsiMethod checkPackageLocalInSuperClass(@NotNull PsiClass aClass) {
    // super class can have package-private abstract methods not accessible for overriding
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) return null;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) return null;
    if (JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) return null;

    for (HierarchicalMethodSignature methodSignature : superClass.getVisibleSignatures()) {
      PsiMethod method = methodSignature.getMethod();
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) return method;
    }
    return null;
  }

  private static boolean add(PsiMethod method, Set<PsiMethod> alreadyImplemented) {
    boolean already = alreadyImplemented.add(method);
    if (!already) return already;

    for (PsiMethod superSig : method.findSuperMethods()) {
      already &= add(superSig, alreadyImplemented);
    }
    return already;
  }
}
