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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PairProcessor;
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
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("ScopedClassHierarchy");
  private final PsiClass myPlaceClass;
  private final GlobalSearchScope myResolveScope;
  private volatile Map<PsiClass, PsiClassType.ClassResolveResult> mySupersWithSubstitutors;
  private volatile List<PsiClassType.ClassResolveResult> myImmediateSupersWithCapturing;
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final Map<LanguageLevel, Map<PsiClass, PsiSubstitutor>> myAllSupersWithCapturing = new ConcurrentFactoryMap<LanguageLevel, Map<PsiClass, PsiSubstitutor>>() {
    @Nullable
    @Override
    protected Map<PsiClass, PsiSubstitutor> create(LanguageLevel key) {
      return calcAllMemberSupers(key);
    }
  };

  private ScopedClassHierarchy(PsiClass psiClass, GlobalSearchScope resolveScope) {
    myPlaceClass = psiClass;
    myResolveScope = resolveScope;
  }

  private void visitType(@NotNull PsiClassType type, Map<PsiClass, PsiClassType.ClassResolveResult> map) {
    PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null || InheritanceImplUtil.hasObjectQualifiedName(psiClass) || map.containsKey(psiClass)) {
      return;
    }

    map.put(psiClass, resolveResult);

    for (PsiType superType : getSuperTypes(psiClass)) {
      superType = type.isRaw() && superType instanceof PsiClassType ? ((PsiClassType)superType).rawType() : resolveResult.getSubstitutor().substitute(superType);
      superType = PsiClassImplUtil.correctType(superType, myResolveScope);
      if (superType instanceof PsiClassType) {
        visitType((PsiClassType)superType, map);
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
  static ScopedClassHierarchy getHierarchy(@NotNull final PsiClass psiClass, @NotNull final GlobalSearchScope resolveScope) {
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
    ScopedClassHierarchy hierarchy = getHierarchy(derivedClass, scope);
    Map<PsiClass, PsiClassType.ClassResolveResult> map = hierarchy.mySupersWithSubstitutors;
    if (map == null) {
      map = ContainerUtil.newTroveMap(CLASS_HASHING_STRATEGY);
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      hierarchy.visitType(JavaPsiFacade.getElementFactory(derivedClass.getProject()).createType(derivedClass, PsiSubstitutor.EMPTY), map);
      if (stamp.mayCacheNow()) {
        hierarchy.mySupersWithSubstitutors = map;
      }
    }
    PsiClassType.ClassResolveResult resolveResult = map.get(superClass);
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

  @NotNull
  List<PsiClassType.ClassResolveResult> getImmediateSupersWithCapturing() {
    List<PsiClassType.ClassResolveResult> list = myImmediateSupersWithCapturing;
    if (list == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      list = ourGuard.doPreventingRecursion(this, true, new Computable<List<PsiClassType.ClassResolveResult>>() {
        @Override
        public List<PsiClassType.ClassResolveResult> compute() {
          return calcImmediateSupersWithCapturing();
        }
      });
      if (list == null) {
        return Collections.emptyList();
      }
      if (stamp.mayCacheNow()) {
        myImmediateSupersWithCapturing = list;
      }
    }
    return list;
  }

  @NotNull
  private List<PsiClassType.ClassResolveResult> calcImmediateSupersWithCapturing() {
    List<PsiClassType.ClassResolveResult> list;
    list = ContainerUtil.newArrayList();
    for (PsiClassType type : myPlaceClass.getSuperTypes()) {
      PsiClassType corrected = PsiClassImplUtil.correctType(type, myResolveScope);
      if (corrected == null) continue;

      PsiClassType.ClassResolveResult result = ((PsiClassType)PsiUtil.captureToplevelWildcards(corrected, myPlaceClass)).resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null || !PsiSearchScopeUtil.isInScope(myResolveScope, superClass)) continue;

      list.add(result);
    }
    return list;
  }

  @NotNull
  private Map<PsiClass, PsiSubstitutor> calcAllMemberSupers(final LanguageLevel level) {
    final Map<PsiClass, PsiSubstitutor> map = ContainerUtil.newTroveMap();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myPlaceClass.getProject());
    new PairProcessor<PsiClass, PsiSubstitutor>() {
      @Override
      public boolean process(PsiClass eachClass, PsiSubstitutor eachSubstitutor) {
        if (!map.containsKey(eachClass)) {
          map.put(eachClass, eachSubstitutor);
          PsiClassImplUtil.processSuperTypes(eachClass, eachSubstitutor, factory, level, myResolveScope, this);
        }
        return true;
      }
    }.process(myPlaceClass, PsiSubstitutor.EMPTY);
    return map;
  }

  @Nullable
  PsiSubstitutor getSuperMembersSubstitutor(@NotNull PsiClass superClass, @NotNull LanguageLevel level) {
    return myAllSupersWithCapturing.get(level).get(superClass);
  }
}
