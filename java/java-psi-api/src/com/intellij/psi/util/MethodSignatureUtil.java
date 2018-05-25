/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MethodSignatureUtil {
  private MethodSignatureUtil() { }

  public static final TObjectHashingStrategy<MethodSignatureBackedByPsiMethod> METHOD_BASED_HASHING_STRATEGY =
    new TObjectHashingStrategy<MethodSignatureBackedByPsiMethod>() {
      @Override
      public int computeHashCode(final MethodSignatureBackedByPsiMethod signature) {
        return signature.getMethod().hashCode();
      }

      @Override
      public boolean equals(final MethodSignatureBackedByPsiMethod s1, final MethodSignatureBackedByPsiMethod s2) {
        return s1.getMethod().equals(s2.getMethod());
      }
    };

  public static final TObjectHashingStrategy<MethodSignature> METHOD_PARAMETERS_ERASURE_EQUALITY =
    new TObjectHashingStrategy<MethodSignature>() {
      @Override
      public int computeHashCode(final MethodSignature signature) {
        return signature.hashCode();
      }

      @Override
      public boolean equals(MethodSignature method1, MethodSignature method2) {
        return areSignaturesEqualLightweight(method1, method2) && areErasedParametersEqual(method1, method2);
      }
    };

  /**
   * def: (8.4.2 Method Signature) Two method signatures m1 and m2 are override-equivalent iff either m1 is a subsignature of m2 or m2 is a subsignature of m1.
   *
   * erasure (erasure) = erasure, so we would check if erasures are equal and then check if the number of type parameters agree: 
   *      if signature(m1)=signature(m2), then m1.typeParams=m2.typeParams
   *      if (erasure(signature(m1))=signature(m2), then m2.typeParams.length=0 and vise versa
   */
  public static boolean areOverrideEquivalent(PsiMethod method1, PsiMethod method2) {
    final int typeParamsLength1 = method1.getTypeParameters().length;
    final int typeParamsLength2 = method2.getTypeParameters().length;
    return (typeParamsLength1 == typeParamsLength2 || typeParamsLength1 == 0 || typeParamsLength2 == 0) &&
           areErasedParametersEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }
  
  public static boolean areErasedParametersEqual(@NotNull MethodSignature method1, @NotNull MethodSignature method2) {
    PsiType[] erased1 = method1 instanceof MethodSignatureBase
                        ? ((MethodSignatureBase)method1).getErasedParameterTypes() : calcErasedParameterTypes(method1);
    PsiType[] erased2 = method2 instanceof MethodSignatureBase
                        ? ((MethodSignatureBase)method2).getErasedParameterTypes() : calcErasedParameterTypes(method2);
    return Arrays.equals(erased1, erased2);
  }

  @NotNull
  public static PsiType[] calcErasedParameterTypes(@NotNull MethodSignature signature) {
    PsiType[] parameterTypes = signature.getParameterTypes();
    if (parameterTypes.length == 0) return PsiType.EMPTY_ARRAY;

    PsiSubstitutor substitutor = signature.getSubstitutor();
    PsiType[] erasedTypes = PsiType.createArray(parameterTypes.length);
    for (int i = 0; i < parameterTypes.length; i++) {
      erasedTypes[i] = TypeConversionUtil.erasure(parameterTypes[i], substitutor);
    }
    return erasedTypes;
  }

  @NotNull
  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @Nullable PsiParameterList parameterTypes,
                                                      @Nullable PsiTypeParameterList typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  @NotNull
  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @Nullable PsiParameterList parameterTypes,
                                                      @Nullable PsiTypeParameterList typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      boolean isConstructor) {
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor, isConstructor);
  }

  @NotNull
  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @NotNull PsiType[] parameterTypes,
                                                      @NotNull PsiTypeParameter[] typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  @NotNull
  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @NotNull PsiType[] parameterTypes,
                                                      @NotNull PsiTypeParameter[] typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      boolean isConstructor) {
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor, isConstructor);
  }

  public static boolean areSignaturesEqual(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    return areSignaturesEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesEqual(@NotNull MethodSignature method1, @NotNull MethodSignature method2) {
    if (method2 == method1) return true;
    if (!areSignaturesEqualLightweight(method1, method2)) return false;
    return checkSignaturesEqualInner(method1, method2, getSuperMethodSignatureSubstitutor(method1, method2))
           || checkSignaturesEqualInner(method2, method1, getSuperMethodSignatureSubstitutor(method2, method1));
  }

  private static boolean checkSignaturesEqualInner(@NotNull MethodSignature subSignature,
                                                   @NotNull MethodSignature superSignature,
                                                   final PsiSubstitutor unifyingSubstitutor) {
    if (unifyingSubstitutor == null) return false;
    if (!areErasedParametersEqual(subSignature, superSignature)) return false;

    final PsiType[] subParameterTypes = subSignature.getParameterTypes();
    final PsiType[] superParameterTypes = superSignature.getParameterTypes();
    for (int i = 0; i < subParameterTypes.length; i++) {
      final PsiType type1 = unifyingSubstitutor.substitute(subParameterTypes[i]);
      final PsiType type2 = unifyingSubstitutor.substitute(superParameterTypes[i]);
      if (!Comparing.equal(type1, type2)) {
        return false;
      }
    }

    return true;
  }

  private static boolean areSignaturesEqualLightweight(@NotNull MethodSignature sig1, @NotNull MethodSignature sig2) {
    final boolean isConstructor1 = sig1.isConstructor();
    final boolean isConstructor2 = sig2.isConstructor();
    if (isConstructor1 != isConstructor2) return false;

    if (!isConstructor1 || !(sig1 instanceof HierarchicalMethodSignature || sig2 instanceof HierarchicalMethodSignature)) {
      final String name1 = sig1.getName();
      final String name2 = sig2.getName();
      if (!name1.equals(name2)) return false;
    }

    final PsiType[] parameterTypes1 = sig1.getParameterTypes();
    final PsiType[] parameterTypes2 = sig2.getParameterTypes();
    if (parameterTypes1.length != parameterTypes2.length) return false;

    // optimization: check for really different types in method parameters
    for (int i = 0; i < parameterTypes1.length; i++) {
      final PsiType type1 = parameterTypes1[i];
      final PsiType type2 = parameterTypes2[i];
      if (type1 instanceof PsiPrimitiveType != type2 instanceof PsiPrimitiveType) return false;
      if (type1 instanceof PsiPrimitiveType && !type1.equals(type2)) return false;
    }

    return true;
  }

  public static boolean isSuperMethod(@NotNull PsiMethod superMethodCandidate, @NotNull PsiMethod derivedMethod) {
    if (superMethodCandidate.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    PsiClass superClassCandidate = superMethodCandidate.getContainingClass();
    PsiClass derivedClass = derivedMethod.getContainingClass();
    if (derivedClass == null || superClassCandidate == null || derivedClass == superClassCandidate) return false;
    final PsiSubstitutor superSubstitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(superClassCandidate, derivedClass,
                                                                                             PsiSubstitutor.EMPTY, null);
    if (superSubstitutor == null) return false;
    final MethodSignature superSignature = superMethodCandidate.getSignature(superSubstitutor);
    final MethodSignature derivedSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
    return isSubsignature(superSignature, derivedSignature);
  }

  @Nullable
  public static PsiMethod findMethodInSuperClassBySignatureInDerived(@NotNull final PsiClass aClass,
                                                                     @NotNull final PsiClass superClass,
                                                                     @NotNull MethodSignature signature,
                                                                     final boolean checkDeep) {
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
    return doFindMethodInSuperClassBySignatureInDerived(superClass, superSubstitutor, signature, checkDeep);
  }

  @Nullable
  private static PsiMethod doFindMethodInSuperClassBySignatureInDerived(@NotNull PsiClass superClass,
                                                                        @NotNull PsiSubstitutor superSubstitutor,
                                                                        @NotNull MethodSignature signature,
                                                                        final boolean checkDeep) {
    final String name = signature.getName();
    final PsiMethod[] methods = superClass.findMethodsByName(name, false);
    for (final PsiMethod method : methods) {
      if (isSubsignature(method.getSignature(superSubstitutor), signature)) {
        return method;
      }
    }

    if (checkDeep) {
      final PsiClass clazz = superClass.getSuperClass();
      if (clazz != null && clazz != superClass) {
        PsiSubstitutor substitutor1 = TypeConversionUtil.getSuperClassSubstitutor(clazz, superClass, superSubstitutor);
        return doFindMethodInSuperClassBySignatureInDerived(clazz, substitutor1, signature, true);
      }
    }

    return null;
  }

  @Nullable
  public static PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod pattenMethod, boolean checkBases) {
    return findMethodBySignature(aClass, pattenMethod.getSignature(PsiSubstitutor.EMPTY), checkBases);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull MethodSignature methodSignature, boolean checkBases) {
    String name = methodSignature.isConstructor() ? aClass.getName() : methodSignature.getName();
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(name, checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (methodSignature.equals(foundMethodSignature)) return method;
    }
    return null;
  }

  @Nullable
  public static PsiMethod findMethodBySuperSignature(@NotNull PsiClass aClass, @NotNull MethodSignature methodSignature, final boolean checkBases) {
    String name = methodSignature.isConstructor() ? aClass.getName() : methodSignature.getName();
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(name, checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (isSubsignature(methodSignature, foundMethodSignature)) return method;
    }
    return null;
  }

  @Nullable
  public static PsiMethod findMethodBySuperMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method, final boolean checkBases) {
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(method.getName(), checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod candidate = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature candidateSignature = candidate.getSignature(substitutor);
      final PsiClass methodClass = method.getContainingClass();
      final PsiClass candidateClass = candidate.getContainingClass();
      if (methodClass == null || candidateClass == null) continue;
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(methodClass, candidateClass, substitutor);
      if (superSubstitutor == null) continue;
      MethodSignature superSignature = method.getSignature(superSubstitutor);
      if (isSubsignature(superSignature, candidateSignature)) return candidate;
    }
    return null;
  }

  public static boolean hasOverloads(@NotNull PsiMethod method) {
    return getOverloads(method).length > 1;
  }

  @NotNull
  private static PsiMethod[] getOverloads(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    return aClass.findMethodsByName(method.getName(), false);
  }

  public static boolean areParametersErasureEqual(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    if (method1.getParameterList().getParametersCount() != method2.getParameterList().getParametersCount()) return false;
    return areSignaturesErasureEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesErasureEqual(@NotNull MethodSignature signature1, @NotNull MethodSignature signature2) {
    return METHOD_PARAMETERS_ERASURE_EQUALITY.equals(signature1, signature2);
  }

  /**
   * * 8.4.4 Generic Methods :: same type parameters condition
   * Two methods or constructors M and N have the same type parameters if both of the following are true:
   * * M and N have same number of type parameters (possibly zero).
   * * Where A1, ..., An are the type parameters of M and B1, ..., Bn are the type parameters of N, let theta=[B1:=A1, ..., Bn:=An].
   *   Then, for all i (1 <= i <= n), the bound of Ai is the same type as theta applied to the bound of Bi.
   * 
   * @param methodSignature method signature
   * @param superMethodSignature super method signature
   * @return null if signatures do not match
   */
  @Nullable
  public static PsiSubstitutor getSuperMethodSignatureSubstitutor(@NotNull MethodSignature methodSignature, @NotNull MethodSignature superMethodSignature) {
    PsiTypeParameter[] methodTypeParameters = methodSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameters = superMethodSignature.getTypeParameters();

    // both methods are parameterized and number of parameters mismatch
    if (methodTypeParameters.length != superTypeParameters.length) return null;

    PsiSubstitutor result = superMethodSignature.getSubstitutor();
    for (int i = 0; i < methodTypeParameters.length; i++) {
      PsiTypeParameter methodTypeParameter = methodTypeParameters[i];
      PsiElementFactory factory = JavaPsiFacade.getInstance(methodTypeParameter.getProject()).getElementFactory();
      result = result.put(superTypeParameters[i], factory.createType(methodTypeParameter));
    }

    final PsiSubstitutor methodSubstitutor = methodSignature.getSubstitutor();

    //check bounds
    for (int i = 0; i < methodTypeParameters.length; i++) {
      PsiTypeParameter methodTypeParameter = methodTypeParameters[i];
      PsiTypeParameter superTypeParameter = superTypeParameters[i];
      final Set<PsiType> methodSupers = new HashSet<>();
      for (PsiClassType methodSuper : methodTypeParameter.getSuperTypes()) {
        methodSupers.add(methodSubstitutor.substitute(methodSuper));
      }

      final Set<PsiType> superSupers = new HashSet<>();
      for (PsiClassType superSuper : superTypeParameter.getSuperTypes()) {
        superSupers.add(methodSubstitutor.substitute(result.substitute(superSuper)));
      }
      methodSupers.remove(PsiType.getJavaLangObject(methodTypeParameter.getManager(), methodTypeParameter.getResolveScope()));
      superSupers.remove(PsiType.getJavaLangObject(superTypeParameter.getManager(), superTypeParameter.getResolveScope()));
      if (!methodSupers.equals(superSupers)) return null;
    }

    return result;
  }

  @NotNull
  public static PsiSubstitutor combineSubstitutors(@NotNull PsiSubstitutor substitutor1, @NotNull PsiSubstitutor substitutor2) {
    if (substitutor1 == PsiSubstitutor.EMPTY) return substitutor2;
    Set<PsiTypeParameter> parameters1 = substitutor1.getSubstitutionMap().keySet();
    final PsiTypeParameter[] typeParameters = parameters1.toArray(PsiTypeParameter.EMPTY_ARRAY);
    for (PsiTypeParameter typeParameter : typeParameters) {
      final PsiType type = substitutor1.substitute(typeParameter);
      PsiType otherSubstituted;
      if (type instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)type).resolve();
        otherSubstituted = resolved instanceof PsiTypeParameter ? substitutor2.substitute((PsiTypeParameter)resolved) : substitutor2.substitute(type);
      }
      else {
        otherSubstituted = substitutor2.substitute(type);
      }

      substitutor1 = substitutor1.put(typeParameter, otherSubstituted);
    }
    return substitutor1;
  }

  @NotNull
  public static PsiMethod[] convertMethodSignaturesToMethods(@NotNull List<? extends MethodSignatureBackedByPsiMethod> sameNameMethodList) {
    final PsiMethod[] methods = new PsiMethod[sameNameMethodList.size()];
    for (int i = 0; i < sameNameMethodList.size(); i++) {
      methods[i] = sameNameMethodList.get(i).getMethod();
    }
    return methods;
  }

  public static boolean isSubsignature(@NotNull MethodSignature superSignature, @NotNull MethodSignature subSignature) {
    if (subSignature == superSignature) return true;
    if (!areSignaturesEqualLightweight(superSignature, subSignature)) return false;
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(subSignature, superSignature);
    if (checkSignaturesEqualInner(superSignature, subSignature, unifyingSubstitutor)) return true;

    if (subSignature.getTypeParameters().length > 0) return false;
    final PsiType[] subParameterTypes = subSignature.getParameterTypes();
    final PsiType[] superParameterTypes = superSignature.getParameterTypes();
    for (int i = 0; i < subParameterTypes.length; i++) {
      PsiType type1 = subParameterTypes[i];
      PsiType type2 = TypeConversionUtil.erasure(superParameterTypes[i], superSignature.getSubstitutor());
      if (!Comparing.equal(type1, type2)) return false;
    }
    return true;
  }


  /**
   * 8.4.5 Method Result :: return type substitutable
   */
  public static boolean isReturnTypeSubstitutable(MethodSignature d1, MethodSignature d2, PsiType r1, PsiType r2) {
    //If R1 is void then R2 is void.
    if (PsiType.VOID.equals(r1)) {
      return PsiType.VOID.equals(r2);
    }

    //If R1 is a primitive type then R2 is identical to R1.
    if (r1 instanceof PsiPrimitiveType) {
      return r1.equals(r2);
    }

    if (r1 instanceof PsiClassType && r2 != null) {

      //R1, adapted to the type parameters of d2 (p8.4.4), is a subtype of R2.
      final PsiSubstitutor adaptingSubstitutor = getSuperMethodSignatureSubstitutor(d2, d1);
      if (adaptingSubstitutor != null && r2.isAssignableFrom(adaptingSubstitutor.substitute(r1))) {
        return true;
      }

      //d1 does not have the same signature as d2 (p8.4.2), and R1 = |R2|.
      if (!areSignaturesEqual(d1, d2)) {
        return r1.equals(TypeConversionUtil.erasure(r2));
      }
    }

    return Comparing.equal(r1, r2);
  }
}
