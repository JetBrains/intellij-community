// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public final class PsiSuperMethodImplUtil {
  private static final Logger LOG = Logger.getInstance(PsiSuperMethodImplUtil.class);

  private static final Key<CachedValue<Map<MethodSignature, HierarchicalMethodSignature>>> SIGNATURES_FOR_CLASS_KEY =
    Key.create("SIGNATURES_FOR_CLASS");
  private static final Key<CachedValue<Map<Pair<String, GlobalSearchScope>, Map<MethodSignature, HierarchicalMethodSignature>>>> SIGNATURES_BY_NAME_KEY =
    Key.create("SIGNATURES_BY_NAME");

  private PsiSuperMethodImplUtil() {
  }

  public static PsiMethod @NotNull [] findSuperMethods(@NotNull PsiMethod method) {
    return findSuperMethods(method, null);
  }

  public static PsiMethod @NotNull [] findSuperMethods(@NotNull PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, null);
  }

  public static PsiMethod @NotNull [] findSuperMethods(@NotNull PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  private static PsiMethod @NotNull [] findSuperMethodsInternal(@NotNull PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  @NotNull
  public static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(@NotNull PsiMethod method,
                                                                                                boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.emptyList();
    return findSuperMethodSignatures(method, null, true);
  }

  @NotNull
  private static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(@NotNull PsiMethod method,
                                                                                  PsiClass parentClass,
                                                                                  boolean allowStaticMethod) {
    return new ArrayList<>(SuperMethodsSearch.search(new SuperMethodsSearch.SearchParameters(method, parentClass, true, allowStaticMethod, true)).findAll());
  }

  private static boolean canHaveSuperMethod(@NotNull PsiMethod method, boolean checkAccess, boolean allowStaticMethod) {
    if (method.isConstructor()) return false;
    if (!allowStaticMethod && method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (checkAccess && method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    PsiClass parentClass = method.getContainingClass();
    return parentClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName());
  }

  @Nullable
  public static PsiMethod findDeepestSuperMethod(@NotNull PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return null;
    return DeepestSuperMethodsSearch.search(method).findFirst();
  }

  public static PsiMethod @NotNull [] findDeepestSuperMethods(@NotNull PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  private static Map<MethodSignature, HierarchicalMethodSignature> buildMethodHierarchy(@NotNull PsiClass aClass,
                                                                                        @Nullable String nameHint,
                                                                                        @NotNull PsiSubstitutor substitutor,
                                                                                        final boolean includePrivates,
                                                                                        @NotNull final Set<? super PsiClass> visited,
                                                                                        boolean isInRawContext,
                                                                                        GlobalSearchScope resolveScope) {
    ProgressManager.checkCanceled();
    Map<MethodSignature, HierarchicalMethodSignature> result = new LinkedHashMap<>(
      new EqualityPolicy<MethodSignature>() {
        @Override
        public int getHashCode(MethodSignature object) {
          return object.hashCode();
        }

        @Override
        public boolean isEqual(MethodSignature o1, MethodSignature o2) {
          if (o1.equals(o2)) {
            final PsiMethod method1 = ((MethodSignatureBackedByPsiMethod)o1).getMethod();
            final PsiType returnType1 = method1.getReturnType();
            final PsiMethod method2 = ((MethodSignatureBackedByPsiMethod)o2).getMethod();
            final PsiType returnType2 = method2.getReturnType();
            if (method1.hasModifierProperty(PsiModifier.STATIC) || method2.hasModifierProperty(PsiModifier.STATIC)) {
              return true;
            }

            if (MethodSignatureUtil.isReturnTypeSubstitutable(o1, o2, returnType1, returnType2)) {
              return true;
            }

            final PsiClass containingClass1 = method1.getContainingClass();
            final PsiClass containingClass2 = method2.getContainingClass();
            if (containingClass1 != null && containingClass2 != null) {
              return containingClass1.isAnnotationType() || containingClass2.isAnnotationType();
            }
          }
          return false;
        }
      });
    Map<MethodSignature, List<PsiMethod>> sameParameterErasureMethods = MethodSignatureUtil.createErasedMethodSignatureMap();

    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = new LinkedHashMap<>(new EqualityPolicy<MethodSignature>() {
      @Override
      public int getHashCode(MethodSignature signature) {
        return signature.hashCode();
      }

      @Override
      public boolean isEqual(MethodSignature o1, MethodSignature o2) {
        if (!MethodSignatureUtil.areSignaturesErasureEqual(o1, o2)) return false;
        List<PsiMethod> list = sameParameterErasureMethods.get(o1);
        boolean toCheckReturnType = list != null && list.size() > 1;
        if (!toCheckReturnType) return true;
        PsiType returnType1 = ((MethodSignatureBackedByPsiMethod)o1).getMethod().getReturnType();
        PsiType returnType2 = ((MethodSignatureBackedByPsiMethod)o2).getMethod().getReturnType();
        if (returnType1 == null && returnType2 == null) return true;
        if (returnType1 == null || returnType2 == null) return false;

        PsiType erasure1 = TypeConversionUtil.erasure(o1.getSubstitutor().substitute(returnType1));
        PsiType erasure2 = TypeConversionUtil.erasure(o2.getSubstitutor().substitute(returnType2));
        return erasure1.equals(erasure2);
      }
    });

    PsiMethod[] methods = nameHint == null ? aClass.getMethods() : aClass.findMethodsByName(nameHint, false);
    for (PsiMethod method : methods) {
      PsiUtilCore.ensureValid(method);
      if (!includePrivates && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY, isInRawContext);
      HierarchicalMethodSignatureImpl newH = new HierarchicalMethodSignatureImpl(MethodSignatureBackedByPsiMethod.create(method, substitutor, isInRawContext));

      List<PsiMethod> list = sameParameterErasureMethods.get(signature);
      if (list == null) {
        list = new SmartList<>();
        sameParameterErasureMethods.put(signature, list);
      }
      list.add(method);

      LOG.assertTrue(newH.getMethod().isValid(), newH.getMethod().getClass());
      result.put(signature, newH);
      map.put(signature, newH);
    }

    final List<PsiClassType.ClassResolveResult> superTypes = PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, resolveScope);

    for (PsiClassType.ClassResolveResult superTypeResolveResult : superTypes) {
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      if (!visited.add(superClass)) continue; // cyclic inheritance
      final PsiSubstitutor superSubstitutor = superTypeResolveResult.getSubstitutor();
      PsiSubstitutor finalSubstitutor = PsiSuperMethodUtil.obtainFinalSubstitutor(superClass, superSubstitutor, substitutor, isInRawContext);

      final boolean isInRawContextSuper = (isInRawContext || PsiUtil.isRawSubstitutor(superClass, superSubstitutor)) && superClass.getTypeParameters().length != 0;
      Map<MethodSignature, HierarchicalMethodSignature> superResult = buildMethodHierarchy(superClass, nameHint, finalSubstitutor, false, visited, isInRawContextSuper, resolveScope);
      visited.remove(superClass);

      List<Pair<MethodSignature, HierarchicalMethodSignature>> flattened = new ArrayList<>();
      for (Map.Entry<MethodSignature, HierarchicalMethodSignature> entry : superResult.entrySet()) {
        HierarchicalMethodSignature hms = entry.getValue();
        MethodSignature signature = MethodSignatureBackedByPsiMethod.create(hms.getMethod(), hms.getSubstitutor(), hms.isRaw());
        PsiClass containingClass = hms.getMethod().getContainingClass();
        List<HierarchicalMethodSignature> supers = new ArrayList<>(hms.getSuperSignatures());
        for (HierarchicalMethodSignature aSuper : supers) {
          PsiClass superContainingClass = aSuper.getMethod().getContainingClass();
          if (containingClass != null && superContainingClass != null && !containingClass.isInheritor(superContainingClass, true)) {
            // methods must be inherited from unrelated classes, so flatten hierarchy here
            // class C implements SAM1, SAM2 { void methodimpl() {} }
            //hms.getSuperSignatures().remove(aSuper);
            flattened.add(Pair.create(signature, aSuper));
          }
        }
        putInMap(aClass, result, map, hms, signature);
      }
      for (Pair<MethodSignature, HierarchicalMethodSignature> pair : flattened) {
        putInMap(aClass, result, map, pair.second, pair.first);
      }
    }


    for (Map.Entry<MethodSignature, HierarchicalMethodSignatureImpl> entry : map.entrySet()) {
      HierarchicalMethodSignatureImpl hierarchicalMethodSignature = entry.getValue();
      MethodSignature methodSignature = entry.getKey();
      if (result.get(methodSignature) == null) {
        LOG.assertTrue(hierarchicalMethodSignature.getMethod().isValid());
        result.put(methodSignature, hierarchicalMethodSignature);
      }
    }

    return result;
  }

  private static void putInMap(@NotNull PsiClass aClass,
                               @NotNull Map<MethodSignature, HierarchicalMethodSignature> result,
                               @NotNull Map<MethodSignature, HierarchicalMethodSignatureImpl> map,
                               @NotNull HierarchicalMethodSignature hierarchicalMethodSignature,
                               @NotNull MethodSignature signature) {
    if (isInheritedInterfaceStaticMethod(hierarchicalMethodSignature, aClass)) {
      return;
    }
    HierarchicalMethodSignatureImpl existing = map.get(signature);
    if (existing == null) {
      HierarchicalMethodSignatureImpl copy = copy(hierarchicalMethodSignature);
      LOG.assertTrue(copy.getMethod().isValid());
      map.put(signature, copy);
      return;
    }

    MergeSuperType mergeSuperType = solveMergeSuperType(aClass, hierarchicalMethodSignature, existing);
    if ((isReturnTypeIsMoreSpecificThan(hierarchicalMethodSignature, existing) &&
         mergeSuperType == MergeSuperType.MERGE_SUPER) || mergeSuperType == MergeSuperType.FORCE_MERGE) {
      HierarchicalMethodSignatureImpl newCurrent = copy(hierarchicalMethodSignature);
      mergeSupers(newCurrent, existing);
      LOG.assertTrue(newCurrent.getMethod().isValid());
      map.put(signature, newCurrent);
      return;
    }

    mergeSuperType = solveMergeSuperType(aClass, existing, hierarchicalMethodSignature);
    if (mergeSuperType == MergeSuperType.MERGE_SUPER || mergeSuperType == MergeSuperType.FORCE_MERGE) {
      mergeSupers(existing, hierarchicalMethodSignature);
      return;
    }
    // just drop an invalid method declaration there - to highlight accordingly
    if (!result.containsKey(signature)) {
      LOG.assertTrue(hierarchicalMethodSignature.getMethod().isValid());
      result.put(signature, hierarchicalMethodSignature);
    }
  }

  private static boolean isInheritedInterfaceStaticMethod(@NotNull HierarchicalMethodSignature signature, @NotNull PsiClass aClass) {
    //static methods from interfaces are not inheritable
    //jls 8, 8.4.8. and 9.4.1., so we need to skip them
    PsiMethod method = signature.getMethod();
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null &&
        !aClass.getManager().areElementsEquivalent(aClass, containingClass) &&
        containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      return true;
    }
    return false;
  }

  private static boolean isReturnTypeIsMoreSpecificThan(@NotNull HierarchicalMethodSignature thisSig, @NotNull HierarchicalMethodSignature thatSig) {
    PsiType thisRet = thisSig.getSubstitutor().substitute(thisSig.getMethod().getReturnType());
    PsiType thatRet = thatSig.getSubstitutor().substitute(thatSig.getMethod().getReturnType());
    PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.isSubsignature(thatSig, thisSig)
                                         ? MethodSignatureUtil.getSuperMethodSignatureSubstitutor(thisSig, thatSig) : null;
    if (unifyingSubstitutor != null) {
      thisRet = unifyingSubstitutor.substitute(thisRet);
      thatRet = unifyingSubstitutor.substitute(thatRet);
    }
    if (thatRet == null || thisRet == null) {
      return false;
    }
    if (!thatRet.isValid()) {
      PsiUtilCore.ensureValid(thatSig.getMethod());
      PsiUtil.ensureValidType(thatRet);
    }
    if (!thisRet.isValid()) {
      PsiUtilCore.ensureValid(thisSig.getMethod());
      PsiUtil.ensureValidType(thisRet);
    }
    return !thatRet.equals(thisRet) && TypeConversionUtil.isAssignable(thatRet, thisRet, false);
  }

  private static void mergeSupers(@NotNull HierarchicalMethodSignatureImpl existing, @NotNull HierarchicalMethodSignature superSignature) {
    for (HierarchicalMethodSignature existingSuper : existing.getSuperSignatures()) {
      if (existingSuper.getMethod() == superSignature.getMethod()) {
        for (HierarchicalMethodSignature signature : superSignature.getSuperSignatures()) {
          mergeSupers((HierarchicalMethodSignatureImpl)existingSuper, signature);
        }
        return;
      }
    }
    if (existing.getMethod() == superSignature.getMethod()) {
      List<HierarchicalMethodSignature> existingSupers = existing.getSuperSignatures();
      for (HierarchicalMethodSignature supers : superSignature.getSuperSignatures()) {
        if (!existingSupers.contains(supers)) existing.addSuperSignature(copy(supers));
      }
    }
    else {
      HierarchicalMethodSignatureImpl copy = copy(superSignature);
      existing.addSuperSignature(copy);
    }
  }

  private enum MergeSuperType{

    MERGE_SUPER,
    NOT_MERGE_SUPER,
    FORCE_MERGE
  }

  /**
   *
   * @return
   * - MERGE_SUPER if {@code superSignatureHierarchical} can be used as super method of {@code hierarchicalMethodSignature} regarding its return type
   * - NOT_MERGE_SUPER if it cannot be used as a super method. See  {@link PsiSuperMethodImplUtil#putInMap(PsiClass, Map, Map, HierarchicalMethodSignature, MethodSignature)}
   * - FORCE_MERGE if this method must be applied as a super method not taking into account its return type
   */
  @NotNull
  private static MergeSuperType solveMergeSuperType(@NotNull PsiClass aClass,
                                                    @NotNull MethodSignatureBackedByPsiMethod hierarchicalMethodSignature,
                                                    @NotNull MethodSignatureBackedByPsiMethod superSignatureHierarchical) {
    PsiMethod superMethod = superSignatureHierarchical.getMethod();
    PsiClass superClass = superMethod.getContainingClass();
    PsiMethod method = hierarchicalMethodSignature.getMethod();
    PsiClass containingClass = method.getContainingClass();
    if (!superMethod.isConstructor() &&
        !aClass.equals(superClass) &&
        MethodSignatureUtil.isSubsignature(superSignatureHierarchical, hierarchicalMethodSignature) && superClass != null) {
      if (superClass.isInterface() ||
          CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        if (superMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
            //static method can't be from interface (see com.intellij.psi.impl.PsiSuperMethodImplUtil.canBeStoreInMap),
            // so it is inheritable, then according to
            //jls 8, 8.4.8.2. it is a compile-time error
            method.hasModifierProperty(PsiModifier.STATIC) &&
            !InheritanceUtil.isInheritorOrSelf(containingClass, superClass, true)) {
          //return method as base, it will be processed by
          // com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil.checkOverrideEquivalentInheritedMethods
          //it requires that static method must be a base
          return MergeSuperType.FORCE_MERGE;
        }

        if (superMethod.hasModifierProperty(PsiModifier.DEFAULT) || method.hasModifierProperty(PsiModifier.DEFAULT)) {
          boolean isSuper = superMethod.equals(method) || !InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true);
          return isSuper ? MergeSuperType.MERGE_SUPER : MergeSuperType.NOT_MERGE_SUPER;
        }
        return MergeSuperType.MERGE_SUPER;
      }

      if (containingClass != null) {
        if (containingClass.isInterface()) {
          return MergeSuperType.NOT_MERGE_SUPER;
        }

        if (!aClass.isInterface() && !InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true)) {
          return MergeSuperType.MERGE_SUPER;
        }
      }
    }
    return MergeSuperType.NOT_MERGE_SUPER;
  }

  @NotNull
  private static HierarchicalMethodSignatureImpl copy(@NotNull HierarchicalMethodSignature hi) {
    HierarchicalMethodSignatureImpl hierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(hi);
    for (HierarchicalMethodSignature his : hi.getSuperSignatures()) {
      hierarchicalMethodSignature.addSuperSignature(copy(his));
    }
    return hierarchicalMethodSignature;
  }

  @NotNull
  public static Collection<HierarchicalMethodSignature> getVisibleSignatures(@NotNull PsiClass aClass) {
    Map<MethodSignature, HierarchicalMethodSignature> map = getSignaturesMap(aClass);
    return map.values();
  }

  @NotNull
  public static HierarchicalMethodSignature getHierarchicalMethodSignature(@NotNull PsiMethod method) {
    return getHierarchicalMethodSignature(method, method.getResolveScope());
  }

  @NotNull
  public static HierarchicalMethodSignature getHierarchicalMethodSignature(@NotNull PsiMethod method, @NotNull GlobalSearchScope resolveScope) {
    Map<GlobalSearchScope, HierarchicalMethodSignature> signatures = CachedValuesManager.getCachedValue(method, () -> {
      ConcurrentMap<GlobalSearchScope, HierarchicalMethodSignature> map = ConcurrentFactoryMap.createMap(scope -> {
        PsiClass aClass = method.getContainingClass();
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        HierarchicalMethodSignature result = null;
        if (aClass != null) {
          result = getSignaturesByName(aClass)
            .get(Pair.create(method.getName(), scope)).get(signature);
        }
        if (result == null) {
          result = new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)signature);
        }
        return result;
      });
      return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return signatures.get(resolveScope);
  }

  private static @NotNull Map<Pair<String, GlobalSearchScope>, Map<MethodSignature, HierarchicalMethodSignature>> getSignaturesByName(@NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, SIGNATURES_BY_NAME_KEY, () ->
      Result.create(ConcurrentFactoryMap.createMap(pair -> buildMethodHierarchy(psiClass, pair.first, PsiSubstitutor.EMPTY, true, new HashSet<>(), false, pair.second)),
                    PsiModificationTracker.MODIFICATION_COUNT)
    );
  }

  private static @NotNull Map<MethodSignature, HierarchicalMethodSignature> getSignaturesMap(@NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, SIGNATURES_FOR_CLASS_KEY, () ->
      Result.create(buildMethodHierarchy(psiClass, null, PsiSubstitutor.EMPTY, true, new HashSet<>(), false, psiClass.getResolveScope()),
                    PsiModificationTracker.MODIFICATION_COUNT)
    );
  }

  // uses hierarchy signature tree if available, traverses class structure by itself otherwise
  public static boolean processDirectSuperMethodsSmart(@NotNull PsiMethod method, @NotNull Processor<? super PsiMethod> superMethodProcessor) {
    //boolean old = PsiSuperMethodUtil.isSuperMethod(method, superMethod);

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;

    if (!canHaveSuperMethod(method, true, false)) return false;

    Map<MethodSignature, HierarchicalMethodSignature> cachedMap = getSignaturesByName(aClass).get(Pair.create(method.getName(), method.getResolveScope()));
    HierarchicalMethodSignature signature = cachedMap.get(method.getSignature(PsiSubstitutor.EMPTY));
    if (signature != null) {
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
      for (HierarchicalMethodSignature superSignature : superSignatures) {
        if (!superMethodProcessor.process(superSignature.getMethod())) return false;
      }
    }
    return true;
  }
}