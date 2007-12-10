/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ClassUtil {
  public static PsiMethod getAnyAbstractMethod(PsiClass aClass, Collection<HierarchicalMethodSignature> allMethodsCollection) {
    PsiMethod methodToImplement = getAnyMethodToImplement(aClass, allMethodsCollection);
    if (methodToImplement != null) {
      return methodToImplement;
    }
    PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    return abstractPackageLocalMethod(aClass, allMethodsCollection);
/*
    // the only remaining possiblity for class to have abstract method here is
    //  from package local abstract method defined in inherited class from other package
    PsiManager manager = aClass.getManager();
    for (List<MethodSignatureBackedByPsiMethod> sameSignatureMethods : allMethodsCollection.values()) {
      // look for abstract package locals
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        MethodSignatureBackedByPsiMethod methodSignature1 = sameSignatureMethods.get(i);
        PsiMethod method1 = methodSignature1.getMethod();
        PsiClass class1 = method1.getContainingClass();
        if (class1 == null) {
          sameSignatureMethods.remove(i);
          continue;
        }
        if (!method1.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        // check if abstract package local method gets overriden by not abstract
        // i.e. there is direct subclass in the same package which overrides this method
        for (int j = sameSignatureMethods.size() - 1; j >= 0; j--) {
          MethodSignatureBackedByPsiMethod methodSignature2 = sameSignatureMethods.get(j);
          PsiMethod method2 = methodSignature2.getMethod();
          PsiClass class2 = method2.getContainingClass();
          if (i == j || class2 == null) continue;
          if (class2.isInheritor(class1, true)
              // NB! overriding method may be abstract
//              && !method2.hasModifierProperty(PsiModifier.ABSTRACT)
&& manager.arePackagesTheSame(class1, class2)) {
            sameSignatureMethods.remove(i);
            break;
          }
        }
      }
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        MethodSignatureBackedByPsiMethod methodSignature = sameSignatureMethods.get(i);
        PsiMethod method = methodSignature.getMethod();
        PsiClass class1 = method.getContainingClass();
        if (class1 == null
            || !method.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        return method;
      }
    }
    return null;
*/
  }

  private static PsiMethod abstractPackageLocalMethod(PsiClass aClass, Collection<HierarchicalMethodSignature> allMethodsCollection) {
    Set<PsiMethod> allMethods = new THashSet<PsiMethod>(Arrays.asList(aClass.getAllMethods()));
    Set<PsiMethod> suspects = new THashSet<PsiMethod>();
    // check all methods collection first for sibling overrides
    for (HierarchicalMethodSignature signature : allMethodsCollection) {
      removeSupers(signature, allMethods, suspects);
      PsiMethod method = signature.getMethod();
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        suspects.add(method);
      }
      allMethods.remove(method);
    }
    while (!allMethods.isEmpty()) {
      PsiMethod method = allMethods.iterator().next();
      removeSupers(method.getHierarchicalMethodSignature(), allMethods, suspects);
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        suspects.add(method);
      }
      allMethods.remove(method);
    }
    return suspects.isEmpty() ? null : suspects.iterator().next();
  }

  private static void removeSupers(HierarchicalMethodSignature hierarchicalMethodSignature, Set<PsiMethod> allMethods, Set<PsiMethod> suspects) {
    for (HierarchicalMethodSignature superS : hierarchicalMethodSignature.getSuperSignatures()) {
      PsiMethod superMethod = superS.getMethod();
      allMethods.remove(superMethod);
      suspects.remove(superMethod);
      assert superS != hierarchicalMethodSignature;
      removeSupers(superS, allMethods, suspects);
    }
  }

  public static PsiMethod getAnyMethodToImplement(PsiClass aClass, Collection<HierarchicalMethodSignature> allMethodsCollection) {
    for (HierarchicalMethodSignature signatureHierarchical : allMethodsCollection) {
      final PsiMethod method = signatureHierarchical.getMethod();
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        continue;
      }
      if (!aClass.equals(containingClass) &&
          method.hasModifierProperty(PsiModifier.ABSTRACT)
          && !method.hasModifierProperty(PsiModifier.STATIC)
          && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return method;
      }
      else {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
        final List<HierarchicalMethodSignature> superSignatures = signatureHierarchical.getSuperSignatures();
        for (HierarchicalMethodSignature superSignatureHierarchical : superSignatures) {
          final PsiMethod superMethod = superSignatureHierarchical.getMethod();
          if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT) && !resolveHelper.isAccessible(superMethod, method, null)) return superMethod;
        }
      }
    }

    return null;
  }

}