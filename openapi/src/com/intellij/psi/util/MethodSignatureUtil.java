/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import gnu.trove.Equality;

import java.util.*;


public class MethodSignatureUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureUtil");

  private static final Key<CachedValue<MethodSignatureToMethods>> METHOD_SIGNATURES_COLLECTION_KEY = Key.create("METHOD_SIGNATURES_COLLECTION");

  public static MethodSignature createMethodSignature(String name,
                                                      PsiParameterList parameterTypes,
                                                      PsiTypeParameterList typeParameterList,
                                                      PsiSubstitutor substitutor) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(substitutor != null);
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor);
  }

  public static MethodSignature createMethodSignature(String name,
                                                      PsiType[] parameterTypes,
                                                      PsiTypeParameter[] typeParameterList,
                                                      PsiSubstitutor substitutor) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(substitutor != null);
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor);
  }

  private static final Equality<MethodSignature> METHOD_PARAMETERS_ERASURE_EQUALITY = new MethodParametersErasureEquality();

  /**
   * @deprecated use areSignaturesEqual() which takes correct substitutors
   */
  public static boolean areSignaturesEqual(PsiMethod method1, PsiMethod method2) {
    return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesEqual(MethodSignature method1, MethodSignature method2) {
    if (method2 == method1) return true;
    if (checkDifferentSignaturesLightweight(method1, method2)) return false;
    if (method1.isRaw() && !method2.isRaw()) {
      PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(method2, method1);
      return checkSignaturesEqualInner(method2, method1, unifyingSubstitutor);
    }
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(method1, method2);
    return checkSignaturesEqualInner(method1, method2, unifyingSubstitutor);
  }

  private static boolean checkSignaturesEqualInner(final MethodSignature subSignature,
                                                  final MethodSignature superSignature,
                                                  PsiSubstitutor unifyingSubstitutor) {
    if (unifyingSubstitutor == null) return false;
    PsiTypeParameter[] subTypeParameters = subSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameters = superSignature.getTypeParameters();
    if (subTypeParameters.length > superTypeParameters.length) return false;
    for (int i = 0; i < subTypeParameters.length; i++) {
      PsiType type1 = unifyingSubstitutor.substitute(subTypeParameters[i]);
      PsiType type2 = unifyingSubstitutor.substitute(superTypeParameters[i]);
      if (!Comparing.equal(type1, type2)) return false;
    }

    final PsiType[] subParameterTypes = subSignature.getParameterTypes();
    final PsiType[] superParameterTypes = superSignature.getParameterTypes();
    for (int i = 0; i < subParameterTypes.length; i++) {
      PsiType type1 = unifyingSubstitutor.substitute(subParameterTypes[i]);
      PsiType type2 = unifyingSubstitutor.substitute(superParameterTypes[i]);
      if (!type1.equals(type2)) {
        if (subSignature.getTypeParameters().length > 0) return false;
        return checkErasures(subParameterTypes, superParameterTypes);
      }
    }

    return true;
  }

  private static boolean checkDifferentSignaturesLightweight (MethodSignature sig1, MethodSignature sig2) {
    String name1 = sig1.getName();
    String name2 = sig2.getName();
    if (!name1.equals(name2)) return true;
    final PsiType[] parameterTypes1 = sig1.getParameterTypes();
    final PsiType[] parameterTypes2 = sig2.getParameterTypes();
    if (parameterTypes1.length != parameterTypes2.length) return true;

    // optimization: check for really different types in method parameters
    for (int i = 0; i < parameterTypes1.length; i++) {
      PsiType type1 = parameterTypes1[i];
      PsiType type2 = parameterTypes2[i];
      if (type1 instanceof PsiClassType != type2 instanceof PsiClassType) return true;
      if (type1 instanceof PsiPrimitiveType != type2 instanceof PsiPrimitiveType) return true;
      if (type1 instanceof PsiPrimitiveType && !type1.equals(type2)) return true;
    }

    return false;
  }

  private static boolean checkErasures(final PsiType[] subParamTypes, final PsiType[] superParamTypes) {
    for (int i = 0; i < subParamTypes.length; i++) {
      final PsiType type1 = TypeConversionUtil.erasure(superParamTypes[i]);
      final PsiType type2 = subParamTypes[i];
      if (!type1.equals(type2)) return false;
    }
    return true;
  }

  // TODO: add substitutor to signature
  public static boolean areSignaturesEqual(PsiPointcutDef method1, PsiPointcutDef method2) {
    String name1 = method1.getName();
    String name2 = method2.getName();
    if (!name1.equals(name2)) return false;

    PsiParameter[] parms1 = method1.getParameterList().getParameters();
    PsiParameter[] parms2 = method2.getParameterList().getParameters();
    return areParametersEqual(parms1, parms2);
  }

  private static boolean areParametersEqual(PsiParameter[] parms1, PsiParameter[] parms2) {
    return areParametersEqual(parms1, PsiSubstitutor.EMPTY, parms2, PsiSubstitutor.EMPTY);
  }

  private static boolean areParametersEqual(PsiParameter[] parms1,
                                            PsiSubstitutor substitutor1,
                                            PsiParameter[] parms2,
                                            PsiSubstitutor substitutor2) {

    if (parms1.length != parms2.length) return false;
    for (int i = 0; i < parms1.length; i++) {
      PsiType type1 = substitutor1.substitute(parms1[i].getType());
      PsiType type2 = substitutor2.substitute(parms2[i].getType());
      if (!type1.equals(type2)) return false;
    }
    return true;
  }

  /**
   * @return Map: PsiMethod method -> List sameSignatureMethods list of methods with the same signatures declared in aClass ancestors including self
   */
  public static MethodSignatureToMethods getSameSignatureMethods(PsiClass aClass) {
    return getSameSignatureMethods(aClass, new HashSet<PsiClass>());
  }

  private static MethodSignatureToMethods getSameSignatureMethods(PsiClass aClass,
                                                                  Set<PsiClass> visitedClasses) {
    CachedValue<MethodSignatureToMethods> cachedValue = aClass.getUserData(METHOD_SIGNATURES_COLLECTION_KEY);
    if (cachedValue == null) {
      MethodSignaturesProvider methodSignaturesProvider = new MethodSignaturesProvider(aClass);
      cachedValue = aClass.getManager().getCachedValuesManager().createCachedValue(methodSignaturesProvider,false);
      aClass.putUserData(METHOD_SIGNATURES_COLLECTION_KEY, cachedValue);
    }

    MethodSignatureToMethods map = cachedValue.getValue();
    if (map == null) {
      LOG.error("Return null for '" + PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_FQ_NAME) + ", " + visitedClasses);
    }
    return map;
  }

  public static class MethodSignatureToMethods {
    private Map<MethodSignature, List<MethodSignatureBackedByPsiMethod>> myMap;

    public MethodSignatureToMethods() {
      myMap = new HashMap<MethodSignature, List<MethodSignatureBackedByPsiMethod>>();
    }

    public List<MethodSignatureBackedByPsiMethod> get(MethodSignature sig) {
      return myMap.get(sig);
    }

    public Collection<List<MethodSignatureBackedByPsiMethod>> values() {
      return myMap.values();
    }

    public void put(MethodSignature methodSignature, List<MethodSignatureBackedByPsiMethod> sameSignatureMethodList) {
      myMap.put(methodSignature, sameSignatureMethodList);
    }
  }

  private static class MethodSignaturesProvider
    implements CachedValueProvider<MethodSignatureToMethods> {
    private final PsiClass myClass;

    MethodSignaturesProvider(PsiClass aClass) {
      myClass = aClass;
    }

    public Result<MethodSignatureToMethods> compute() {
      long start = System.currentTimeMillis();
      try {
        MethodSignatureToMethods sameSignatureMethods = new MethodSignatureToMethods();
        computeMap(sameSignatureMethods, myClass);

        return new Result<MethodSignatureToMethods>
          (sameSignatureMethods,
           new Object[]{myClass, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT});
      }
      finally {
        if (LOG.isDebugEnabled()) {
          long finish = System.currentTimeMillis();
          LOG.debug("Method collection built for " + PsiFormatUtil.formatClass(myClass, PsiFormatUtil.SHOW_FQ_NAME) + " in " +
                    (finish - start) +
                    " ms");
        }
      }
    }

    private static void computeMap(MethodSignatureToMethods sameSignatureMethodsMap, final PsiClass aClass) {
      List<Pair<PsiMethod, PsiSubstitutor>> allMethodsList = aClass.getAllMethodsAndTheirSubstitutors();
      for (Iterator<Pair<PsiMethod, PsiSubstitutor>> iterator = allMethodsList.iterator(); iterator.hasNext();) {
        Pair<PsiMethod, PsiSubstitutor> pair = iterator.next();
        PsiMethod method = pair.getFirst();
        PsiSubstitutor substitutor = pair.getSecond();
        final MethodSignature methodSignature = method.getSignature(substitutor);
        List<MethodSignatureBackedByPsiMethod> sameSignatureMethodList = sameSignatureMethodsMap.get(methodSignature);
        if (sameSignatureMethodList == null) {
          sameSignatureMethodList = new ArrayList<MethodSignatureBackedByPsiMethod>(5);
          sameSignatureMethodsMap.put(methodSignature, sameSignatureMethodList);
        }
        sameSignatureMethodList.add((MethodSignatureBackedByPsiMethod)methodSignature);
      }
    }
  }

  public static List<MethodSignatureBackedByPsiMethod> findMethodSignaturesByName(final PsiClass aClass, String name, boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(name, checkBases);
    if (pairs.size() == 0) return Collections.EMPTY_LIST;
    final List<MethodSignatureBackedByPsiMethod> result = new ArrayList<MethodSignatureBackedByPsiMethod>(pairs.size());
    for (int i = 0; i < pairs.size(); i++) {
      Pair<PsiMethod, PsiSubstitutor> pair = pairs.get(i);
      result.add(new MethodSignatureBackedByPsiMethod(pair.first, pair.second));
    }
    return result;
  }

  public static PsiMethod findMethodBySignature(final PsiClass aClass, PsiMethod pattenMethod, boolean checkBases) {
    return findMethodBySignature(aClass, pattenMethod.getSignature(PsiSubstitutor.EMPTY), checkBases);
  }

  public static PsiMethod findMethodBySignature(final PsiClass aClass, MethodSignature methodSignature, boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(methodSignature.getName(), checkBases);
    for (int i = 0; i < pairs.size(); i++) {
      Pair<PsiMethod, PsiSubstitutor> pair = pairs.get(i);
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (methodSignature.equals(foundMethodSignature)) return method;
    }
    return null;
  }

  public static boolean hasOverloads(PsiMethod method) {
    return getOverloads(method).length > 1;
  }

  public static PsiMethod[] getOverloads(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    return aClass.findMethodsByName(method.getName(), false);
  }

  public static boolean areParametersErasureEqual(PsiMethod method1, PsiMethod method2) {
    return METHOD_PARAMETERS_ERASURE_EQUALITY.equals(method1.getSignature(PsiSubstitutor.EMPTY),
                                                     method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static PsiClass getErasure(PsiClass aClass) {
    if (!(aClass instanceof PsiTypeParameter)) return aClass;
    final PsiClass[] bounds = aClass.getSupers();
    if (bounds.length == 0) return aClass.getManager().findClass("java.lang.Object", aClass.getResolveScope());
    return bounds[0];
  }

  /**
   * @param methodSignature
   * @param superMethodSignature
   * @return null if signatures do not match
   */
  public static PsiSubstitutor getSuperMethodSignatureSubstitutor(MethodSignature methodSignature, MethodSignature superMethodSignature) {
    // normalize generic method declarations: correlate type parameters
    PsiTypeParameter[] typeParameters1 = methodSignature.getTypeParameters();
    PsiTypeParameter[] typeParameters2 = superMethodSignature.getTypeParameters();

    // both methods are parameterized and number of parameters mismatch
    if (typeParameters1.length != typeParameters2.length && typeParameters1.length != 0 && typeParameters2.length != 0) return null;

    boolean derivedRaw = methodSignature.isRaw();
    boolean superRaw = superMethodSignature.isRaw();
    if (derivedRaw || superRaw) {
      if (!superRaw && derivedRaw) return null;  //Wrong order

      PsiSubstitutor substitutor = superMethodSignature.getSubstitutor();
      substitutor = eraseMethodTypeParameters(typeParameters2, substitutor);

      if (derivedRaw) {
        substitutor = eraseMethodTypeParameters(typeParameters1, substitutor);
      }
      return substitutor;
    }

    PsiSubstitutor substitutor1 = methodSignature.getSubstitutor();
    PsiSubstitutor substitutor2 = superMethodSignature.getSubstitutor();
    int limit = Math.min(typeParameters1.length, typeParameters2.length);
    for (int i = 0; i < limit; i++) {
      PsiElementFactory factory = typeParameters1[i].getManager().getElementFactory();
      substitutor2 = substitutor2.put(typeParameters2[i], factory.createType(typeParameters1[i]));
    }

    //check bounds
    for (int i = 0; i < limit; i++) {
      PsiTypeParameter typeParameter1 = typeParameters1[i];
      PsiTypeParameter typeParameter2 = typeParameters2[i];
      final PsiClassType[] supers1 = typeParameter1.getSuperTypes();
      final PsiClassType[] supers2 = typeParameter2.getSuperTypes();
      if (supers1.length != supers2.length) return null;
      for (int j = 0; j < supers1.length; j++) {
        PsiType type1 = substitutor1.substitute(supers1[j]);
        PsiType type2 = substitutor2.substitute(supers2[j]);
        if (!type1.equals(type2)) return null;
      }
    }
    return substitutor2;
  }

  private static PsiSubstitutor eraseMethodTypeParameters(PsiTypeParameter[] typeParameters1, PsiSubstitutor substitutor) {
    for (int i = 0; i < typeParameters1.length; i++) {
      PsiTypeParameter typeParameter = typeParameters1[i];
      substitutor = substitutor.put(typeParameter, null);
    }
    return substitutor;
  }

  public static PsiSubstitutor combineSubstitutors(PsiSubstitutor substitutor, PsiSubstitutor parentSubstitutor) {
    if (substitutor == PsiSubstitutor.EMPTY) return parentSubstitutor;
    final PsiTypeParameter[] typeParameters = substitutor.getSubstitutionMap().keySet().toArray(PsiTypeParameter.EMPTY_ARRAY);
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      final PsiType type = substitutor.substitute(typeParameter);
      substitutor = substitutor.put(typeParameter, promoteType(type, parentSubstitutor));
    }
    return substitutor;
  }

  // null means raw
  private static PsiType promoteType(PsiType type, PsiSubstitutor parentSubstitutor) {
    if (type instanceof PsiTypeParameter) {
      return parentSubstitutor.substitute((PsiTypeParameter)type);
    }
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      final PsiSubstitutor resolvedSubstitutor = resolveResult.getSubstitutor();
      if (aClass != null) {
        final PsiSubstitutor substitutor = combineSubstitutors(resolvedSubstitutor, parentSubstitutor);
        if (aClass instanceof PsiTypeParameter && substitutor.substitute((PsiTypeParameter)aClass) == null) {
          return null;
        }
        type = substitutor.substitute(type);
      }
    }
    return parentSubstitutor.substitute(type);
  }

  private static class MethodParametersErasureEquality implements Equality<MethodSignature> {
    public boolean equals(MethodSignature method1, MethodSignature method2) {
      final PsiType[] parameterTypes1 = method1.getParameterTypes();
      final PsiType[] parameterTypes2 = method2.getParameterTypes();
      if (parameterTypes1.length != parameterTypes2.length) return false;
      for (int i = 0; i < parameterTypes1.length; i++) {
        final PsiType type1 = parameterTypes1[i];
        final PsiType type2 = parameterTypes2[i];
        if (Comparing.equal(type1, type2)) continue;
        if (!(type1 instanceof PsiClassType)) return false;
        if (!(type2 instanceof PsiClassType)) return false;
        final PsiClass aClass1 = getErasure(((PsiClassType)type1).resolve());
        final PsiClass aClass2 = getErasure(((PsiClassType)type2).resolve());
        if (aClass1 != null && !aClass1.getManager().areElementsEquivalent(aClass1, aClass2)) return false;
        if (aClass1 == null != (aClass2 == null)) return false;
      }
      return true;
    }
  }

  public static PsiMethod[] convertMethodSignaturesToMethods(List<MethodSignatureBackedByPsiMethod> sameNameMethodList) {
    final PsiMethod[] methods = new PsiMethod[sameNameMethodList.size()];
    for (int i = 0; i < sameNameMethodList.size(); i++) {
      MethodSignatureBackedByPsiMethod methodBackedMethodSignature = sameNameMethodList.get(i);
      methods[i] = methodBackedMethodSignature.getMethod();
    }
    return methods;
  }
}
