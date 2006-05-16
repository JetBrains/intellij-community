/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public class MethodSignatureUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureUtil");

  private MethodSignatureUtil() {
  }

  public static MethodSignature createMethodSignature(@NonNls String name,
                                                      PsiParameterList parameterTypes,
                                                      PsiTypeParameterList typeParameterList,
                                                      PsiSubstitutor substitutor) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(substitutor != null);
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor);
  }

  public static MethodSignature createMethodSignature(@NonNls String name,
                                                      PsiType[] parameterTypes,
                                                      PsiTypeParameter[] typeParameterList,
                                                      PsiSubstitutor substitutor) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(substitutor != null);
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor);
  }

  public static final TObjectHashingStrategy<MethodSignature> METHOD_PARAMETERS_ERASURE_EQUALITY = new MethodParametersErasureEquality();

  /**
   * @deprecated use areSignaturesEqual() which takes correct substitutors
   */
  public static boolean areSignaturesEqual(PsiMethod method1, PsiMethod method2) {
    return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesEqual(MethodSignature method1, MethodSignature method2) {
    if (method2 == method1) return true;
    if (checkDifferentSignaturesLightweight(method1, method2)) return false;
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(method1, method2);
    return checkSignaturesEqualInner(method1, method2, unifyingSubstitutor);
  }

  private static boolean checkSignaturesEqualInner(final MethodSignature subSignature,
                                                   final MethodSignature superSignature,
                                                   PsiSubstitutor unifyingSubstitutor) {
    if (unifyingSubstitutor == null) return false;

    final PsiType[] subParameterTypes = subSignature.getParameterTypes();
    final PsiType[] superParameterTypes = superSignature.getParameterTypes();
    for (int i = 0; i < subParameterTypes.length; i++) {
      PsiType type1 = unifyingSubstitutor.substitute(subParameterTypes[i]);
      PsiType type2 = unifyingSubstitutor.substitute(superParameterTypes[i]);
      if (!Comparing.equal(type1, type2)) {
        return false;
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
      if (type1 instanceof PsiPrimitiveType != type2 instanceof PsiPrimitiveType) return true;
      if (type1 instanceof PsiPrimitiveType && !type1.equals(type2)) return true;
    }

    return false;
  }

  public static int computeHashCode(final MethodSignature methodSignature) {
    int result = methodSignature.getName().hashCode();

    result += 37 * methodSignature.getParameterTypes().length;
    /*PsiType firstParamType = parameterTypes.length != 0 ? parameterTypes[0] : null;
    if (firstParamType != null) {
      firstParamType = TypeConversionUtil.erasure(firstParamType);
      result += firstParamType.hashCode();
    }*/
    return result;
  }

  public static boolean isSuperMethod(final PsiMethod superMethodCandidate, final PsiMethod derivedMethod) {
    PsiClass superClassCandidate = superMethodCandidate.getContainingClass();
    PsiClass derivedClass = derivedMethod.getContainingClass();
    final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClassCandidate, derivedClass, PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return false;
    final MethodSignature superSignature = superMethodCandidate.getSignature(superSubstitutor);
    final MethodSignature derivedSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
    return MethodSignatureUtil.isSubsignature(superSignature, derivedSignature);
  }

  public static class MethodSignatureToMethods {
    private Map<MethodSignature, List<MethodSignatureBackedByPsiMethod>> myMap;

    public MethodSignatureToMethods() {
      myMap = new THashMap<MethodSignature, List<MethodSignatureBackedByPsiMethod>>(METHOD_PARAMETERS_ERASURE_EQUALITY);
    }

    public List<MethodSignatureBackedByPsiMethod> get(MethodSignature sig) {
      return myMap.get(sig);
    }

    public Collection<List<MethodSignatureBackedByPsiMethod>> values() {
      return myMap.values();
    }

    public void put(MethodSignature methodSignature, List<MethodSignatureBackedByPsiMethod> overrideEquivalentsList) {
      myMap.put(methodSignature, overrideEquivalentsList);
    }
  }

  public static PsiMethod findMethodBySignature(final PsiClass aClass, PsiMethod pattenMethod, boolean checkBases) {
    return findMethodBySignature(aClass, pattenMethod.getSignature(PsiSubstitutor.EMPTY), checkBases);
  }

  public static PsiMethod findMethodBySignature(final PsiClass aClass, MethodSignature methodSignature, boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(methodSignature.getName(), checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (methodSignature.equals(foundMethodSignature)) return method;
    }
    return null;
  }

  public static PsiMethod findMethodBySuperSignature(final PsiClass aClass, MethodSignature methodSignature, final boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(methodSignature.getName(), checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (isSubsignature(methodSignature, foundMethodSignature)) return method;
    }
    return null;
  }

  public static PsiMethod findMethodBySuperMethod(final PsiClass aClass, PsiMethod method, final boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(method.getName(), checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod candidate = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature candidateSignature = candidate.getSignature(substitutor);
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(method.getContainingClass(), candidate.getContainingClass(), substitutor);
      if (superSubstitutor == null) continue;
      MethodSignature superSignature = method.getSignature(superSubstitutor);
      if (isSubsignature(superSignature, candidateSignature)) return candidate;
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

  /**
   * @param methodSignature
   * @param superMethodSignature
   * @return null if signatures do not match
   */
  public static PsiSubstitutor getSuperMethodSignatureSubstitutor(MethodSignature methodSignature, MethodSignature superMethodSignature) {
    PsiSubstitutor result = getSuperMethodSignatureSubstitutorImpl(methodSignature, superMethodSignature);
    if (result == null) return null;

    PsiTypeParameter[] typeParameters1 = methodSignature.getTypeParameters();
    PsiTypeParameter[] typeParameters2 = superMethodSignature.getTypeParameters();
    PsiSubstitutor substitutor1 = methodSignature.getSubstitutor();

    //check bounds
    for (int i = 0; i < typeParameters1.length; i++) {
      PsiTypeParameter typeParameter1 = typeParameters1[i];
      PsiTypeParameter typeParameter2 = typeParameters2[i];
      final PsiClassType[] supers1 = typeParameter1.getSuperTypes();
      final PsiClassType[] supers2 = typeParameter2.getSuperTypes();
      if (supers1.length != supers2.length) return null;
      for (int j = 0; j < supers1.length; j++) {
        PsiType type1 = substitutor1.substitute(supers1[j]);
        PsiType type2 = result.substitute(supers2[j]);
        if (!type1.equals(type2)) return null;
      }
    }
    return result;
  }

  private static PsiSubstitutor getSuperMethodSignatureSubstitutorImpl(MethodSignature signature1, MethodSignature signature2) {
    // normalize generic method declarations: correlate type parameters
    PsiTypeParameter[] typeParameters1 = signature1.getTypeParameters();
    PsiTypeParameter[] typeParameters2 = signature2.getTypeParameters();

    // both methods are parameterized and number of parameters mismatch
    if (typeParameters1.length != typeParameters2.length) return null;

    PsiSubstitutor result = signature2.getSubstitutor();
    for (int i = 0; i < typeParameters1.length; i++) {
      PsiElementFactory factory = typeParameters1[i].getManager().getElementFactory();
      result = result.put(typeParameters2[i], factory.createType(typeParameters1[i]));
    }

    return result;
  }

  public static PsiSubstitutor combineSubstitutors(PsiSubstitutor substitutor1, PsiSubstitutor substitutor2) {
    if (substitutor1 == PsiSubstitutor.EMPTY) return substitutor2;
    final PsiTypeParameter[] typeParameters = substitutor1.getSubstitutionMap().keySet().toArray(PsiTypeParameter.EMPTY_ARRAY);
    for (PsiTypeParameter typeParameter : typeParameters) {
      final PsiType type = substitutor1.substitute(typeParameter);
      PsiType otherSubstituted;
      if (type instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)type).resolve();
        if (resolved instanceof PsiTypeParameter) {
          otherSubstituted = substitutor2.substitute((PsiTypeParameter)resolved);
        } else otherSubstituted = substitutor2.substitute(type);
      } else otherSubstituted = substitutor2.substitute(type);

      substitutor1 = substitutor1.put(typeParameter, otherSubstituted);
    }
    return substitutor1;
  }

  private static class MethodParametersErasureEquality implements TObjectHashingStrategy<MethodSignature> {
    public int computeHashCode(final MethodSignature signature) {
      return MethodSignatureUtil.computeHashCode(signature);
    }

    public boolean equals(MethodSignature method1, MethodSignature method2) {
      if (!method1.getName().equals(method2.getName())) return false;
      final PsiType[] parameterTypes1 = method1.getParameterTypes();
      final PsiType[] parameterTypes2 = method2.getParameterTypes();
      if (parameterTypes1.length != parameterTypes2.length) return false;

      final PsiSubstitutor substitutor1 = method1.getSubstitutor();
      final PsiSubstitutor substitutor2 = method2.getSubstitutor();
      for (int i = 0; i < parameterTypes1.length; i++) {
        final PsiType type1 = TypeConversionUtil.erasure(substitutor1.substitute(parameterTypes1[i]));
        final PsiType type2 = TypeConversionUtil.erasure(substitutor2.substitute(parameterTypes2[i]), substitutor2);
        if (!Comparing.equal(type1, type2)) return false;
      }
      return true;
    }
  }

  public static @NotNull PsiMethod[] convertMethodSignaturesToMethods(List<MethodSignatureBackedByPsiMethod> sameNameMethodList) {
    final PsiMethod[] methods = new PsiMethod[sameNameMethodList.size()];
    for (int i = 0; i < sameNameMethodList.size(); i++) {
      methods[i] = sameNameMethodList.get(i).getMethod();
    }
    return methods;
  }

  public static boolean isSubsignature(MethodSignature superSignature, MethodSignature subSignature) {
    if (subSignature == superSignature) return true;
    if (checkDifferentSignaturesLightweight(superSignature, subSignature)) return false;
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(superSignature, subSignature);
    if (!checkSignaturesEqualInner(superSignature, subSignature, unifyingSubstitutor)) {
      if (subSignature.getTypeParameters().length > 0) return false;
      final PsiType[] subParameterTypes = subSignature.getParameterTypes();
      final PsiType[] superParameterTypes = superSignature.getParameterTypes();
      for (int i = 0; i < subParameterTypes.length; i++) {
        PsiType type1 = subParameterTypes[i];
        PsiType type2 = TypeConversionUtil.erasure(superParameterTypes[i]);
        if (!Comparing.equal(type1, type2)) return false;
      }

      return true;
    }

    return true;
  }

}
