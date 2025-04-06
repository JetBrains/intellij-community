// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.*;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ScopedClassHierarchy {
  private static final HashingStrategy<PsiClass> CLASS_HASHING_STRATEGY = new HashingStrategy<PsiClass>() {
    @Override
    public int hashCode(PsiClass object) {
      return object == null ? 0 : Strings.notNullize(object.getQualifiedName()).hashCode();
    }

    @Override
    public boolean equals(PsiClass o1, PsiClass o2) {
      if (o1 == o2) {
        return true;
      }
      if (o1 == null || o2 == null) {
        return false;
      }

      String qname1 = o1.getQualifiedName();
      if (qname1 != null) {
        return qname1.equals(o2.getQualifiedName());
      }
      return o1.getManager().areElementsEquivalent(o1, o2);
    }
  };
  private final PsiClass myPlaceClass;
  private final GlobalSearchScope myResolveScope;
  private volatile Map<PsiClass, PsiClassType.ClassResolveResult> mySupersWithSubstitutors;
  private volatile List<PsiClassType.ClassResolveResult> myImmediateSupersWithCapturing;
  private final Map<LanguageLevel, Map<PsiClass, PsiSubstitutor>> myAllSupersWithCapturing = ConcurrentFactoryMap.createMap(this::calcAllMemberSupers);

  ScopedClassHierarchy(PsiClass psiClass, GlobalSearchScope resolveScope) {
    myPlaceClass = psiClass;
    myResolveScope = resolveScope;
  }

  void visitType(@NotNull PsiClassType type, Map<PsiClass, PsiClassType.ClassResolveResult> map) {
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

  private static @NotNull List<PsiType> getSuperTypes(PsiClass psiClass) {
    List<PsiType> superTypes = new ArrayList<>();
    if (psiClass instanceof PsiAnonymousClass) {
      ContainerUtil.addIfNotNull(superTypes, ((PsiAnonymousClass)psiClass).getBaseClassType());
    }
    Collections.addAll(superTypes, psiClass.getExtendsListTypes());
    Collections.addAll(superTypes, psiClass.getImplementsListTypes());
    return superTypes;
  }

  static @NotNull ScopedClassHierarchy getHierarchy(final @NotNull PsiClass psiClass, final @NotNull GlobalSearchScope resolveScope) {
    if (psiClass instanceof PsiAnonymousClass) {
      return new ScopedClassHierarchy(psiClass, resolveScope);
    }
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Map<GlobalSearchScope, ScopedClassHierarchy> result =
        ConcurrentFactoryMap.create(resolveScope1 -> new ScopedClassHierarchy(psiClass, resolveScope1),
                                    CollectionFactory::createConcurrentSoftMap);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(resolveScope);
  }

  static @Nullable PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass derivedClass, @NotNull GlobalSearchScope scope, @NotNull PsiClass superClass) {
    ScopedClassHierarchy hierarchy = getHierarchy(derivedClass, scope);
    Map<PsiClass, PsiClassType.ClassResolveResult> map = hierarchy.mySupersWithSubstitutors;
    if (map == null) {
      map = CollectionFactory.createCustomHashingStrategyMap(CLASS_HASHING_STRATEGY);
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      hierarchy.visitType(JavaPsiFacade.getElementFactory(derivedClass.getProject()).createType(derivedClass, PsiSubstitutor.EMPTY), map);
      if (stamp.mayCacheNow()) {
        hierarchy.mySupersWithSubstitutors = map;
      }
    }
    PsiClassType.ClassResolveResult resolveResult = map.get(superClass);
    if (resolveResult == null) return null;

    PsiClass cachedClass = Objects.requireNonNull(resolveResult.getElement());
    PsiSubstitutor cachedSubstitutor = resolveResult.getSubstitutor();
    return cachedClass == superClass ? cachedSubstitutor : mirrorSubstitutor(superClass, cachedClass, cachedSubstitutor);
  }

  private static @NotNull PsiSubstitutor mirrorSubstitutor(@NotNull PsiClass from, final @NotNull PsiClass to, @NotNull PsiSubstitutor substitutor) {
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
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      list = RecursionManager.doPreventingRecursion(this, true, () -> calcImmediateSupersWithCapturing());
      if (list == null) {
        return Collections.emptyList();
      }
      if (stamp.mayCacheNow()) {
        myImmediateSupersWithCapturing = list;
      }
    }
    return list;
  }

  private @NotNull List<PsiClassType.ClassResolveResult> calcImmediateSupersWithCapturing() {
    PsiUtilCore.ensureValid(myPlaceClass);
    List<PsiClassType.ClassResolveResult> list = new ArrayList<>();
    for (PsiClassType type : myPlaceClass.getSuperTypes()) {
      PsiUtil.ensureValidType(type, myPlaceClass);
      PsiClassType corrected = PsiClassImplUtil.correctType(type, myResolveScope);
      if (corrected == null) continue;

      PsiClassType.ClassResolveResult result = ((PsiClassType)PsiUtil.captureToplevelWildcards(corrected, myPlaceClass)).resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null || !PsiSearchScopeUtil.isInScope(myResolveScope, superClass)) continue;

      list.add(result);
    }
    if (list.isEmpty() && myPlaceClass.getExtendsListTypes().length > 0) {
      PsiClassType.ClassResolveResult objectResult = PsiType.getJavaLangObject(myPlaceClass.getManager(), myResolveScope).resolveGenerics();
      if (objectResult.getElement() != null) {
        list.add(objectResult);
      }
    }
    return list;
  }

  private @NotNull Map<PsiClass, PsiSubstitutor> calcAllMemberSupers(final LanguageLevel level) {
    final Map<PsiClass, PsiSubstitutor> map = new HashMap<>();
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
