/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiSuperMethodImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSuperMethodImplUtil");
  private static final PsiCacheKey<Map<MethodSignature, HierarchicalMethodSignature>, PsiClass> SIGNATURES_KEY = PsiCacheKey
    .create("SIGNATURES_KEY", new NotNullFunction<PsiClass, Map<MethodSignature, HierarchicalMethodSignature>>() {
      @NotNull
      @Override
      public Map<MethodSignature, HierarchicalMethodSignature> fun(PsiClass dom) {
        return buildMethodHierarchy(dom, PsiSubstitutor.EMPTY, true, new THashSet<PsiClass>(), false);
      }
    });

  private PsiSuperMethodImplUtil() {
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method) {
    return findSuperMethods(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, null);
  }

  @NotNull
  public static PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    return findSuperMethodsInternal(method, parentClass);
  }


  @NotNull
  private static PsiMethod[] findSuperMethodsInternal(PsiMethod method, PsiClass parentClass) {
    List<MethodSignatureBackedByPsiMethod> outputMethods = findSuperMethodSignatures(method, parentClass, false);

    return MethodSignatureUtil.convertMethodSignaturesToMethods(outputMethods);
  }

  @NotNull
  public static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                         boolean checkAccess) {
    if (!canHaveSuperMethod(method, checkAccess, true)) return Collections.emptyList();
    return findSuperMethodSignatures(method, null, true);
  }

  @NotNull
  private static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignatures(PsiMethod method,
                                                                                  PsiClass parentClass,
                                                                                  boolean allowStaticMethod) {

    return new ArrayList<MethodSignatureBackedByPsiMethod>(SuperMethodsSearch.search(method, parentClass, true, allowStaticMethod).findAll());
  }

  private static boolean canHaveSuperMethod(PsiMethod method, boolean checkAccess, boolean allowStaticMethod) {
    if (method.isConstructor()) return false;
    if (!allowStaticMethod && method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (checkAccess && method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    PsiClass parentClass = method.getContainingClass();
    return parentClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName());
  }

  @Nullable
  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return null;
    return DeepestSuperMethodsSearch.search(method).findFirst();
  }

  public static PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    if (!canHaveSuperMethod(method, true, false)) return PsiMethod.EMPTY_ARRAY;
    Collection<PsiMethod> collection = DeepestSuperMethodsSearch.search(method).findAll();
    return collection.toArray(new PsiMethod[collection.size()]);
  }

  private static Map<MethodSignature, HierarchicalMethodSignature> buildMethodHierarchy(PsiClass aClass,
                                                                                        PsiSubstitutor substitutor,
                                                                                        final boolean includePrivates,
                                                                                        final Set<PsiClass> visited,
                                                                                        boolean isInRawContext) {
    Map<MethodSignature, HierarchicalMethodSignature> result = new LinkedHashMap<MethodSignature, HierarchicalMethodSignature>();
    final Map<MethodSignature, List<PsiMethod>> sameParameterErasureMethods = new THashMap<MethodSignature, List<PsiMethod>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    Map<MethodSignature, HierarchicalMethodSignatureImpl> map = new THashMap<MethodSignature, HierarchicalMethodSignatureImpl>(new TObjectHashingStrategy<MethodSignature>() {
      @Override
      public int computeHashCode(MethodSignature signature) {
        return MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.computeHashCode(signature);
      }

      @Override
      public boolean equals(MethodSignature o1, MethodSignature o2) {
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

    for (PsiMethod method : aClass.getMethods()) {
      if (!method.isValid()) {
        throw new PsiInvalidElementAccessException(method, "class.valid=" + aClass.isValid() + "; name=" + method.getName());
      }
      if (!includePrivates && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, substitutor, isInRawContext);
      HierarchicalMethodSignatureImpl newH = new HierarchicalMethodSignatureImpl(signature);

      List<PsiMethod> list = sameParameterErasureMethods.get(signature);
      if (list == null) {
        list = new SmartList<PsiMethod>();
        sameParameterErasureMethods.put(signature, list);
      }
      list.add(method);

      LOG.assertTrue(newH.getMethod().isValid());
      result.put(signature, newH);
      map.put(signature, newH);
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      if (!visited.add(superClass)) continue; // cyclic inheritance
      final PsiSubstitutor superSubstitutor = superTypeResolveResult.getSubstitutor();
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superSubstitutor, substitutor, isInRawContext);

      final boolean isInRawContextSuper = (isInRawContext || PsiUtil.isRawSubstitutor(superClass, superSubstitutor)) && superClass.getTypeParameters().length != 0;
      Map<MethodSignature, HierarchicalMethodSignature> superResult = buildMethodHierarchy(superClass, finalSubstitutor, false, visited, isInRawContextSuper);
      visited.remove(superClass);

      List<Pair<MethodSignature, HierarchicalMethodSignature>> flattened = new ArrayList<Pair<MethodSignature, HierarchicalMethodSignature>>();
      for (Map.Entry<MethodSignature, HierarchicalMethodSignature> entry : superResult.entrySet()) {
        HierarchicalMethodSignature hms = entry.getValue();
        MethodSignature signature = entry.getKey();
        PsiClass containingClass = hms.getMethod().getContainingClass();
        List<HierarchicalMethodSignature> supers = new ArrayList<HierarchicalMethodSignature>(hms.getSuperSignatures());
        for (HierarchicalMethodSignature aSuper : supers) {
          PsiClass superContainingClass = aSuper.getMethod().getContainingClass();
          if (containingClass != null && superContainingClass != null && !containingClass.isInheritor(superContainingClass, true)) {
            // methods must be inherited from unrelated classes, so flatten hierarchy here
            // class C implements SAM1, SAM2 { void methodimpl() {} }
            //hms.getSuperSignatures().remove(aSuper);
            flattened.add(new Pair<MethodSignature, HierarchicalMethodSignature>(signature, aSuper));
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
      if (result.get(methodSignature) == null && PsiUtil.isAccessible(aClass.getProject(), hierarchicalMethodSignature.getMethod(), aClass, aClass)) {
        LOG.assertTrue(hierarchicalMethodSignature.getMethod().isValid());
        result.put(methodSignature, hierarchicalMethodSignature);
      }
    }

    return result;
  }

  private static void putInMap(PsiClass aClass,
                               Map<MethodSignature, HierarchicalMethodSignature> result,
                               Map<MethodSignature, HierarchicalMethodSignatureImpl> map,
                               HierarchicalMethodSignature hierarchicalMethodSignature,
                               MethodSignature signature) {
    if (!PsiUtil.isAccessible(aClass.getProject(), hierarchicalMethodSignature.getMethod(), aClass, aClass)) return;
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
    return thatRet != null && thisRet != null && !thatRet.equals(thisRet) && TypeConversionUtil.isAssignable(thatRet, thisRet, false);
  }

  private static void mergeSupers(final HierarchicalMethodSignatureImpl existing, final HierarchicalMethodSignature superSignature) {
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

  private static boolean isSuperMethod(PsiClass aClass,
                                       HierarchicalMethodSignature hierarchicalMethodSignature,
                                       HierarchicalMethodSignature superSignatureHierarchical) {
    PsiMethod superMethod = superSignatureHierarchical.getMethod();
    PsiClass superClass = superMethod.getContainingClass();
    PsiClass containingClass = hierarchicalMethodSignature.getMethod().getContainingClass();
    if (!superMethod.isConstructor()) {
      if (!aClass.equals(superClass)) {
        if (PsiUtil.isAccessible(aClass.getProject(), superMethod, aClass, aClass)) {
          if (MethodSignatureUtil.isSubsignature(superSignatureHierarchical, hierarchicalMethodSignature)) {
            if (superClass != null) {
              if (superClass.isInterface() ||
                  CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
                if (superMethod.hasModifierProperty(PsiModifier.DEFAULT) || hierarchicalMethodSignature.getMethod().hasModifierProperty(PsiModifier.DEFAULT)) {
                  return !InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true);
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
          }
        }
      }
    }
    return false;
  }

  private static HierarchicalMethodSignatureImpl copy(HierarchicalMethodSignature hi) {
    HierarchicalMethodSignatureImpl hierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(hi);
    for (HierarchicalMethodSignature his : hi.getSuperSignatures()) {
      hierarchicalMethodSignature.addSuperSignature(copy(his));
    }
    return hierarchicalMethodSignature;
  }

  private static PsiSubstitutor obtainFinalSubstitutor(PsiClass superClass,
                                                       PsiSubstitutor superSubstitutor,
                                                       PsiSubstitutor derivedSubstitutor, boolean inRawContext) {
    if (inRawContext) {
      Set<PsiTypeParameter> typeParams = superSubstitutor.getSubstitutionMap().keySet();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(superClass.getProject());
      superSubstitutor = factory.createRawSubstitutor(derivedSubstitutor, typeParams.toArray(new PsiTypeParameter[typeParams.size()]));
    }
    Map<PsiTypeParameter, PsiType> map = null;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(superClass)) {
      PsiType type = superSubstitutor.substitute(typeParameter);
      final PsiType t = derivedSubstitutor.substitute(type);
      if (map == null) {
        map = new THashMap<PsiTypeParameter, PsiType>();
      }
      map.put(typeParameter, t);
    }

    return map == null ? PsiSubstitutor.EMPTY : JavaPsiFacade.getInstance(superClass.getProject()).getElementFactory().createSubstitutor(map);
  }

  public static Collection<HierarchicalMethodSignature> getVisibleSignatures(PsiClass aClass) {
    Map<MethodSignature, HierarchicalMethodSignature> map = getSignaturesMap(aClass);
    return map.values();
  }

  @NotNull
  public static HierarchicalMethodSignature getHierarchicalMethodSignature(final PsiMethod method) {
    return CachedValuesManager
      .getCachedValue(method, new CachedValueProvider<HierarchicalMethodSignature>() {
        @Nullable
        @Override
        public Result<HierarchicalMethodSignature> compute() {
          PsiClass aClass = method.getContainingClass();
          HierarchicalMethodSignature result = null;
          if (aClass != null) {
            result = getSignaturesMap(aClass).get(method.getSignature(PsiSubstitutor.EMPTY));
          }
          if (result == null) {
            result = new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)method.getSignature(PsiSubstitutor.EMPTY));
          }
          return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
      });
  }

  private static Map<MethodSignature, HierarchicalMethodSignature> getSignaturesMap(final PsiClass aClass) {
    return SIGNATURES_KEY.getValue(aClass);
  }


  // uses hierarchy signature tree if available, traverses class structure by itself otherwise
  public static boolean processDirectSuperMethodsSmart(@NotNull PsiMethod method, @NotNull Processor<PsiMethod> superMethodProcessor) {
    //boolean old = PsiSuperMethodUtil.isSuperMethod(method, superMethod);

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;

    if (!canHaveSuperMethod(method, true, false)) return false;

    Map<MethodSignature, HierarchicalMethodSignature> cachedMap = SIGNATURES_KEY.getCachedValueOrNull(aClass);
    if (cachedMap != null) {
      HierarchicalMethodSignature signature = cachedMap.get(method.getSignature(PsiSubstitutor.EMPTY));
      if (signature != null) {
        List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
        for (HierarchicalMethodSignature superSignature : superSignatures) {
          if (!superMethodProcessor.process(superSignature.getMethod())) return false;
        }
        return true;
      }
    }

    PsiClassType[] directSupers = aClass.getSuperTypes();
    for (PsiClassType directSuper : directSupers) {
      PsiClassType.ClassResolveResult resolveResult = directSuper.resolveGenerics();
      if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
        // generics
        break;
      }
      PsiClass directSuperClass = resolveResult.getElement();
      if (directSuperClass == null) continue;
      PsiMethod[] candidates = directSuperClass.findMethodsBySignature(method, false);
      for (PsiMethod candidate : candidates) {
        if (PsiUtil.canBeOverriden(candidate)) {
          if (!superMethodProcessor.process(candidate)) return false;
        }
      }
      return true;
    }

    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : superSignatures) {
      if (!superMethodProcessor.process(superSignature.getMethod())) return false;
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

    PsiMethod[] superMethods = null;
    Map<MethodSignature, HierarchicalMethodSignature> cachedMap = SIGNATURES_KEY.getCachedValueOrNull(aClass);
    if (cachedMap != null) {
      HierarchicalMethodSignature signature = cachedMap.get(method.getSignature(PsiSubstitutor.EMPTY));
      if (signature != null) {
        superMethods = MethodSignatureUtil.convertMethodSignaturesToMethods(signature.getSuperSignatures());
      }
    }
    if (superMethods == null) {
      PsiClassType[] directSupers = aClass.getSuperTypes();
      List<PsiMethod> found = null;
      boolean canceled = false;
      for (PsiClassType directSuper : directSupers) {
        PsiClassType.ClassResolveResult resolveResult = directSuper.resolveGenerics();
        if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
          // generics
          canceled = true;
          break;
        }
        PsiClass directSuperClass = resolveResult.getElement();
        if (directSuperClass == null) continue;
        PsiMethod[] candidates = directSuperClass.findMethodsBySignature(method, false);
        if (candidates.length != 0) {
          if (found == null) found = new ArrayList<PsiMethod>();
          for (PsiMethod candidate : candidates) {
            if (PsiUtil.canBeOverriden(candidate)) found.add(candidate);
          }
        }
      }
      superMethods = canceled ? null : found == null ? PsiMethod.EMPTY_ARRAY : found.toArray(new PsiMethod[found.size()]);
    }
    if (superMethods == null) {
      superMethods = MethodSignatureUtil.convertMethodSignaturesToMethods(method.getHierarchicalMethodSignature().getSuperSignatures());
    }

    for (PsiMethod superCandidate : superMethods) {
      if (superMethod.equals(superCandidate) || isSuperMethodSmart(superCandidate, superMethod)) return true;
    }
    return false;
  }
}
