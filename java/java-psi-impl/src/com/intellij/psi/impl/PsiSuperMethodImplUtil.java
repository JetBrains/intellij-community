// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSuperMethodImplUtil");
  private static final PsiCacheKey<Map<MethodSignature, HierarchicalMethodSignature>, PsiClass> SIGNATURES_FOR_CLASS_KEY = PsiCacheKey
    .create("SIGNATURES_FOR_CLASS_KEY",
            (NotNullFunction<PsiClass, Map<MethodSignature, HierarchicalMethodSignature>>)dom -> buildMethodHierarchy(dom, null, PsiSubstitutor.EMPTY, true, new THashSet<PsiClass>(), false, dom.getResolveScope()));
  private static final PsiCacheKey<Map<String, Map<MethodSignature, HierarchicalMethodSignature>>, PsiClass> SIGNATURES_BY_NAME_KEY = PsiCacheKey
    .create("SIGNATURES_BY_NAME_KEY", psiClass -> ConcurrentFactoryMap.createMap(
      methodName -> buildMethodHierarchy(psiClass, methodName, PsiSubstitutor.EMPTY, true, new THashSet<>(), false,
                                         psiClass.getResolveScope())));

  private PsiSuperMethodImplUtil() {
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(@NotNull PsiMethod method) {
    return findSuperMethods(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(@NotNull PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(@NotNull PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  @NotNull
  private static PsiMethod[] findSuperMethodsInternal(@NotNull PsiMethod method, PsiClass parentClass) {
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
    return new ArrayList<>(SuperMethodsSearch.search(method, parentClass, true, allowStaticMethod).findAll());
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

  @NotNull
  public static PsiMethod[] findDeepestSuperMethods(@NotNull PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  private static Map<MethodSignature, HierarchicalMethodSignature> buildMethodHierarchy(@NotNull PsiClass aClass,
                                                                                        @Nullable String nameHint,
                                                                                        @NotNull PsiSubstitutor substitutor,
                                                                                        final boolean includePrivates,
                                                                                        @NotNull final Set<PsiClass> visited,
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
    final Map<MethodSignature, List<PsiMethod>> sameParameterErasureMethods =
      new THashMap<>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = new LinkedHashMap<>(new EqualityPolicy<MethodSignature>() {
      @Override
      public int getHashCode(MethodSignature signature) {
        return MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.computeHashCode(signature);
      }

      @Override
      public boolean isEqual(MethodSignature o1, MethodSignature o2) {
        if (!MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals(o1, o2)) return false;
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

    PsiMethod[] methods = aClass.getMethods();
    if ((nameHint == null || "values".equals(nameHint)) && aClass instanceof PsiClassImpl) {
      final PsiMethod valuesMethod = ((PsiClassImpl)aClass).getValuesMethod();
      if (valuesMethod != null) {
        methods = ArrayUtil.append(methods, valuesMethod);
      }
    }

    for (PsiMethod method : methods) {
      if (!method.isValid()) {
        throw new PsiInvalidElementAccessException(method, "class.valid=" + aClass.isValid() + "; name=" + method.getName());
      }
      if (nameHint != null && !nameHint.equals(method.getName())) continue;
      if (!includePrivates && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY, isInRawContext);
      HierarchicalMethodSignatureImpl newH = new HierarchicalMethodSignatureImpl(MethodSignatureBackedByPsiMethod.create(method, substitutor, isInRawContext));

      List<PsiMethod> list = sameParameterErasureMethods.get(signature);
      if (list == null) {
        list = new SmartList<>();
        sameParameterErasureMethods.put(signature, list);
      }
      list.add(method);

      LOG.assertTrue(newH.getMethod().isValid());
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
    HierarchicalMethodSignatureImpl existing = map.get(signature);
    if (existing == null) {
      HierarchicalMethodSignatureImpl copy = copy(hierarchicalMethodSignature);
      LOG.assertTrue(copy.getMethod().isValid());
      map.put(signature, copy);
    }
    else if (isReturnTypeIsMoreSpecificThan(hierarchicalMethodSignature, existing) && isSuperMethod(aClass, hierarchicalMethodSignature, existing)) {
      HierarchicalMethodSignatureImpl newSuper = copy(hierarchicalMethodSignature);
      mergeSupers(newSuper, existing);
      LOG.assertTrue(newSuper.getMethod().isValid());
      map.put(signature, newSuper);
    }
    else if (isSuperMethod(aClass, existing, hierarchicalMethodSignature)) {
      mergeSupers(existing, hierarchicalMethodSignature);
    }
    // just drop an invalid method declaration there - to highlight accordingly
    else if (!result.containsKey(signature)) {
      LOG.assertTrue(hierarchicalMethodSignature.getMethod().isValid());
      result.put(signature, hierarchicalMethodSignature);
    }
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
    return thatRet != null && thisRet != null && !thatRet.equals(thisRet) && TypeConversionUtil.isAssignable(thatRet, thisRet, false);
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

  private static boolean isSuperMethod(@NotNull PsiClass aClass,
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
        if (superMethod.hasModifierProperty(PsiModifier.STATIC) ||
            superMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
            method.hasModifierProperty(PsiModifier.STATIC) &&
            !InheritanceUtil.isInheritorOrSelf(containingClass, superClass, true)) {
          return false;
        }

        if (superMethod.hasModifierProperty(PsiModifier.DEFAULT) || method.hasModifierProperty(PsiModifier.DEFAULT)) {
          return superMethod.equals(method) || !InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true);
        }
        return true;
      }

      if (containingClass != null) {
        if (containingClass.isInterface()) {
          return false;
        }

        if (!aClass.isInterface() && !InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true)) {
          return true;
        }
      }
    }
    return false;
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
  public static HierarchicalMethodSignature getHierarchicalMethodSignature(@NotNull final PsiMethod method) {
    Project project = method.getProject();
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(method, HIERARCHICAL_SIGNATURE_KEY,
                                                                               HIERARCHICAL_SIGNATURE_PROVIDER, false, method);
  }

  private static final Key<ParameterizedCachedValue<HierarchicalMethodSignature, PsiMethod>> HIERARCHICAL_SIGNATURE_KEY = Key.create("HierarchicalMethodSignature");
  private static final ParameterizedCachedValueProvider<HierarchicalMethodSignature, PsiMethod> HIERARCHICAL_SIGNATURE_PROVIDER =
    new ParameterizedCachedValueProvider<HierarchicalMethodSignature, PsiMethod>() {
      @Override
      public CachedValueProvider.Result<HierarchicalMethodSignature> compute(PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        HierarchicalMethodSignature result = null;
        if (aClass != null) {
          result = SIGNATURES_BY_NAME_KEY.getValue(aClass).get(method.getName()).get(method.getSignature(PsiSubstitutor.EMPTY));
        }
        if (result == null) {
          result = new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)method.getSignature(PsiSubstitutor.EMPTY));
        }

        if (!method.isPhysical() && !(method instanceof SyntheticElement) && !(method instanceof LightElement)) {
          return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, method);
        }
        return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    };

  @NotNull
  private static Map<MethodSignature, HierarchicalMethodSignature> getSignaturesMap(@NotNull PsiClass aClass) {
    return SIGNATURES_FOR_CLASS_KEY.getValue(aClass);
  }

  // uses hierarchy signature tree if available, traverses class structure by itself otherwise
  public static boolean processDirectSuperMethodsSmart(@NotNull PsiMethod method, @NotNull Processor<PsiMethod> superMethodProcessor) {
    //boolean old = PsiSuperMethodUtil.isSuperMethod(method, superMethod);

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;

    if (!canHaveSuperMethod(method, true, false)) return false;

    Map<MethodSignature, HierarchicalMethodSignature> cachedMap = SIGNATURES_BY_NAME_KEY.getValue(aClass).get(method.getName());
    HierarchicalMethodSignature signature = cachedMap.get(method.getSignature(PsiSubstitutor.EMPTY));
    if (signature != null) {
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
      for (HierarchicalMethodSignature superSignature : superSignatures) {
        if (!superMethodProcessor.process(superSignature.getMethod())) return false;
      }
    }
    return true;
  }

  // uses hierarchy signature tree if available, traverses class structure by itself otherwise
  public static boolean isSuperMethodSmart(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    //boolean old = PsiSuperMethodUtil.isSuperMethod(method, superMethod);

    if (method == superMethod) return false;
    PsiClass aClass = method.getContainingClass();
    PsiClass superClass = superMethod.getContainingClass();

    if (aClass == null || superClass == null || superClass == aClass) return false;

    if (!canHaveSuperMethod(method, true, false)) return false;

    Map<MethodSignature, HierarchicalMethodSignature> cachedMap = SIGNATURES_BY_NAME_KEY.getValue(aClass).get(method.getName());
    HierarchicalMethodSignature signature = cachedMap.get(method.getSignature(PsiSubstitutor.EMPTY));

    for (PsiMethod superCandidate : MethodSignatureUtil.convertMethodSignaturesToMethods(signature.getSuperSignatures())) {
      if (superMethod.equals(superCandidate) || isSuperMethodSmart(superCandidate, superMethod)) return true;
    }
    return false;
  }
}