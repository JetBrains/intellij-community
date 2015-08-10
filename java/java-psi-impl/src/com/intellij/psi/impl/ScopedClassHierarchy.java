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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
class ScopedClassHierarchy {
  private static final TObjectHashingStrategy<PsiClass> CLASS_HASHING_STRATEGY = new TObjectHashingStrategy<PsiClass>() {
    @Override
    public int computeHashCode(PsiClass object) {
      return StringUtil.notNullize(object.getQualifiedName()).hashCode();
    }

    @Override
    public boolean equals(PsiClass o1, PsiClass o2) {
      final String qname1 = o1.getQualifiedName();
      if (qname1 != null) return qname1.equals(o2.getQualifiedName());

      return o1.getManager().areElementsEquivalent(o1, o2);
    }
  };
  private final Map<PsiClass, PsiClassType.ClassResolveResult> mySupersWithSubstitutors = ContainerUtil.newTroveMap(CLASS_HASHING_STRATEGY);

  private ScopedClassHierarchy(PsiClass psiClass, GlobalSearchScope resolveScope) {
    visitType(resolveScope, JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, PsiSubstitutor.EMPTY));
  }

  private void visitType(GlobalSearchScope resolveScope, @NotNull PsiClassType type) {
    PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null || InheritanceImplUtil.hasObjectQualifiedName(psiClass) || mySupersWithSubstitutors.containsKey(psiClass)) {
      return;
    }

    mySupersWithSubstitutors.put(psiClass, resolveResult);

    for (PsiType superType : getSuperTypes(psiClass)) {
      superType = type.isRaw() && superType instanceof PsiClassType ? ((PsiClassType)superType).rawType() : resolveResult.getSubstitutor().substitute(superType);
      superType = PsiClassImplUtil.correctType(superType, resolveScope);
      if (superType instanceof PsiClassType) {
        visitType(resolveScope, (PsiClassType)superType);
      }
    }
  }

  @NotNull
  private static List<PsiType> getSuperTypes(PsiClass psiClass) {
    List<PsiType> superTypes = ContainerUtil.newArrayList();
    if (psiClass instanceof PsiAnonymousClass) {
      ContainerUtil.addIfNotNull(superTypes, ((PsiAnonymousClass)psiClass).getBaseClassType());
    }
    Collections.addAll(superTypes, psiClass.getExtendsListTypes());
    Collections.addAll(superTypes, psiClass.getImplementsListTypes());
    return superTypes;
  }

  @NotNull
  private static ScopedClassHierarchy getHierarchy(@NotNull final PsiClass psiClass, @NotNull final GlobalSearchScope resolveScope) {
    return CachedValuesManager.getCachedValue(psiClass, new CachedValueProvider<Map<GlobalSearchScope, ScopedClassHierarchy>>() {
      @Nullable
      @Override
      public Result<Map<GlobalSearchScope, ScopedClassHierarchy>> compute() {
        Map<GlobalSearchScope, ScopedClassHierarchy> result = new ConcurrentFactoryMap<GlobalSearchScope, ScopedClassHierarchy>() {
          @Nullable
          @Override
          protected ScopedClassHierarchy create(GlobalSearchScope resolveScope) {
            return new ScopedClassHierarchy(psiClass, resolveScope);
          }
        };
        return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    }).get(resolveScope);
  }

  @Nullable
  static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass derivedClass, @NotNull GlobalSearchScope scope, @NotNull PsiClass superClass) {
    PsiClassType.ClassResolveResult resolveResult = getHierarchy(derivedClass, scope).mySupersWithSubstitutors.get(superClass);
    if (resolveResult == null) return null;

    PsiClass cachedClass = assertNotNull(resolveResult.getElement());
    PsiSubstitutor cachedSubstitutor = resolveResult.getSubstitutor();
    return cachedClass == superClass ? cachedSubstitutor : mirrorSubstitutor(superClass, cachedClass, cachedSubstitutor);
  }

  @NotNull
  private static PsiSubstitutor mirrorSubstitutor(@NotNull PsiClass from, @NotNull final PsiClass to, @NotNull PsiSubstitutor substitutor) {
    Iterator<PsiTypeParameter> baseParams = PsiUtil.typeParametersIterator(to);
    Iterator<PsiTypeParameter> candidateParams = PsiUtil.typeParametersIterator(from);

    PsiSubstitutor answer = PsiSubstitutor.EMPTY;
    while (baseParams.hasNext()) {
      // if equivalent classes "from" and "to" have different number of type parameters, then treat "to" as a raw type
      if (!candidateParams.hasNext()) return JavaClassSupersImpl.createRawSubstitutor(to); 

      answer = answer.put(baseParams.next(), substitutor.substitute(candidateParams.next()));
    }
    return answer;
  }

}
