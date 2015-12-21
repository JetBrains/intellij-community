/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FindSuperElementsHelper {
  @NotNull
  public static PsiElement[] findSuperElements(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass) element;
      List<PsiClass> allSupers = new ArrayList<PsiClass>(Arrays.asList(aClass.getSupers()));
      for (Iterator<PsiClass> iterator = allSupers.iterator(); iterator.hasNext();) {
        PsiClass superClass = iterator.next();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) iterator.remove();
      }
      return allSupers.toArray(new PsiClass[allSupers.size()]);
    }
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor()) {
        PsiMethod constructorInSuper = PsiSuperMethodUtil.findConstructorInSuper(method);
        if (constructorInSuper != null) {
          return new PsiMethod[]{constructorInSuper};
        }
      }
      else {
        PsiMethod[] superMethods = method.findSuperMethods(false);
        if (superMethods.length == 0) {
          PsiMethod superMethod = getSiblingInheritedViaSubClass(method);
          if (superMethod != null) {
            superMethods = new PsiMethod[]{superMethod};
          }
        }
        return superMethods;
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  public static PsiMethod getSiblingInheritedViaSubClass(@NotNull PsiMethod method) {
    return Pair.getFirst(getSiblingInheritedViaSubClass(method, createSubClassCache()));
  }

  // returns super method, sub class
  public static Pair<PsiMethod, PsiClass> getSiblingInheritedViaSubClass(@NotNull final PsiMethod method,
                                                                         @NotNull Map<PsiClass, PsiClass> subClassCache) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return null;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass containingClass = method.getContainingClass();
    boolean hasSubClass = containingClass != null && !containingClass.isInterface() && subClassCache.get(containingClass) != null;
    if (!hasSubClass) {
      return null;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
      return null;
    }
    final Collection<PsiAnchor> checkedInterfaces = new THashSet<PsiAnchor>();
    checkedInterfaces.add(PsiAnchor.create(containingClass));
    final Ref<Pair<PsiMethod, PsiClass>> result = Ref.create();
    ClassInheritorsSearch.search(containingClass, containingClass.getUseScope(), true, true, false).forEach(new Processor<PsiClass>() {
      @Override
      public boolean process(PsiClass inheritor) {
        ProgressManager.checkCanceled();
        for (PsiClassType interfaceType : inheritor.getImplementsListTypes()) {
          ProgressManager.checkCanceled();
          PsiClassType.ClassResolveResult resolved = interfaceType.resolveGenerics();
          PsiClass anInterface = resolved.getElement();
          if (anInterface == null || !checkedInterfaces.add(PsiAnchor.create(anInterface))) continue;
          for (PsiMethod superMethod : anInterface.findMethodsByName(method.getName(), true)) {
            PsiElement navigationElement = superMethod.getNavigationElement();
            if (!(navigationElement instanceof PsiMethod)) continue; // Kotlin
            superMethod = (PsiMethod)navigationElement;
            ProgressManager.checkCanceled();
            PsiClass superInterface = superMethod.getContainingClass();
            if (superInterface == null) {
              continue;
            }
            if (containingClass.isInheritor(superInterface, true)) {
              // if containingClass implements the superInterface then it's not a sibling inheritance but a pretty boring the usual one
              continue;
            }

            // calculate substitutor of containingClass --> inheritor
            PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
            // calculate substitutor of inheritor --> superInterface
            substitutor = TypeConversionUtil.getSuperClassSubstitutor(superInterface, inheritor, substitutor);

            final MethodSignature superSignature = superMethod.getSignature(substitutor);
            final MethodSignature derivedSignature = method.getSignature(PsiSubstitutor.EMPTY);
            boolean isOverridden = MethodSignatureUtil.isSubsignature(superSignature, derivedSignature);

            if (!isOverridden) {
              continue;
            }
            result.set(Pair.create(superMethod, inheritor));
            return false;
          }
        }
        return true;
      }
    });
    return result.get();
  }

  @NotNull
  public static Map<PsiClass, PsiClass> createSubClassCache() {
    return new FactoryMap<PsiClass, PsiClass>() {
        @Nullable
        @Override
        protected PsiClass create(PsiClass aClass) {
          return ClassInheritorsSearch.search(aClass, false).findFirst();
        }
      };
  }
}
