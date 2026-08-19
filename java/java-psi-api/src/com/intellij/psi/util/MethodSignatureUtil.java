// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MethodSignatureUtil {
  private MethodSignatureUtil() { }

  private static final HashingStrategy<MethodSignature> METHOD_PARAMETERS_ERASURE_STRATEGY =
    new HashingStrategy<MethodSignature>() {
      @Override
      public int hashCode(final MethodSignature signature) {
        return signature == null ? 0 : erasedSignatureHashCode(signature);
      }

      @Override
      public boolean equals(@Nullable MethodSignature method1, @Nullable MethodSignature method2) {
        return method1 == method2 || (method1 != null && method2 != null && areSignaturesEqualLightweight(method1, method2) && areErasedParametersEqual(method1, method2));
      }
    };

  public static @NotNull <V> Map<MethodSignature, V> createErasedMethodSignatureMap() {
    return CollectionFactory.createCustomHashingStrategyMap(METHOD_PARAMETERS_ERASURE_STRATEGY);
  }

  public static @NotNull Set<MethodSignature> createErasedMethodSignatureSet() {
    return CollectionFactory.createCustomHashingStrategySet(METHOD_PARAMETERS_ERASURE_STRATEGY);
  }

  /// Two method signatures `m1` and `m2` are override-equivalent iff either `m1` is a subsignature of `m2`
  /// or `m2` is a subsignature of `m1`.
  /// 
  /// See JLS 8.4.2 Method Signature
  /// 
  /// `erasure (erasure) = erasure`, so we would check if erasures are equal and then check if the number of type parameters agrees:
  /// - if `signature(m1) = signature(m2)`, then `m1.typeParams=m2.typeParams`
  /// - if `erasure(signature(m1)) = signature(m2)`, then `m2.typeParams.length=0` and vice versa
  public static boolean areOverrideEquivalent(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    final int typeParamsLength1 = method1.getTypeParameters().length;
    final int typeParamsLength2 = method2.getTypeParameters().length;
    return (typeParamsLength1 == typeParamsLength2 || typeParamsLength1 == 0 || typeParamsLength2 == 0) &&
           areErasedParametersEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areErasedParametersEqual(@NotNull MethodSignature method1, @NotNull MethodSignature method2) {
    PsiType[] erased1 = getErasedParameterTypes(method1);
    PsiType[] erased2 = getErasedParameterTypes(method2);
    return Arrays.equals(erased1, erased2) ||
           differOnlyInTypeParameterBoundsOrder(method1, method2, erased1, erased2) && areSignaturesEqual(method1, method2);
  }

  /**
   * The erasure of a type variable is the erasure of its leftmost bound (JLS 4.6), while the bound of a type parameter is an
   * intersection type whose components may be listed in any order (JLS 8.4.4). Hence, two generic methods which differ only in
   * the order of the bounds of their type parameters have the same signature, but different erasures; javac accepts such an
   * override and generates a bridge method for it. Such methods are still override-equivalent, so they must be reported as
   * erasure-equal, otherwise they end up in different buckets of an erased method signature map.
   *
   * @return true if every parameter whose erasure differs between the two signatures is a type parameter of the respective
   * method, and the two type parameters declare the same bounds in a different order
   */
  private static boolean differOnlyInTypeParameterBoundsOrder(@NotNull MethodSignature method1,
                                                              @NotNull MethodSignature method2,
                                                              PsiType @NotNull [] erased1,
                                                              PsiType @NotNull [] erased2) {
    if (erased1.length != erased2.length) return false;
    List<PsiTypeParameter> typeParameters1 = Arrays.asList(method1.getTypeParameters());
    List<PsiTypeParameter> typeParameters2 = Arrays.asList(method2.getTypeParameters());
    if (typeParameters1.isEmpty() || typeParameters1.size() != typeParameters2.size()) return false;
    PsiType[] parameterTypes1 = method1.getParameterTypes();
    PsiType[] parameterTypes2 = method2.getParameterTypes();
    for (int i = 0; i < erased1.length; i++) {
      if (Comparing.equal(erased1[i], erased2[i])) continue;
      int index = typeParameters1.indexOf(resolveTypeParameter(parameterTypes1[i]));
      if (index < 0 || index != typeParameters2.indexOf(resolveTypeParameter(parameterTypes2[i]))) return false;
      if (!haveSameIntersectionBound(typeParameters1.get(index), typeParameters2.get(index))) return false;
    }
    return true;
  }

  private static @Nullable PsiTypeParameter resolveTypeParameter(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    return ObjectUtils.tryCast(((PsiClassType)type).resolve(), PsiTypeParameter.class);
  }

  private static boolean haveSameIntersectionBound(@NotNull PsiTypeParameter typeParameter1, @NotNull PsiTypeParameter typeParameter2) {
    PsiClassType[] bounds1 = typeParameter1.getExtendsListTypes();
    PsiClassType[] bounds2 = typeParameter2.getExtendsListTypes();
    return bounds1.length > 1 && bounds1.length == bounds2.length &&
           ContainerUtil.newHashSet(bounds1).equals(ContainerUtil.newHashSet(bounds2));
  }

  /**
   * @return a hash code of the signature which is compatible with {@link #areErasedParametersEqual} and with
   * {@link #areSignaturesEqual}. It is based on the erased parameter types, except that the erasure of a type variable with an
   * intersection bound is taken to be the erasure of its canonically first bound instead of the erasure of its leftmost bound
   * (JLS 4.6): the declaration order of the bounds does not affect signature equality (JLS 8.4.4), the canonical order does.
   * <p>
   * Two signatures may still be {@linkplain #areErasedParametersEqual erasure-equal} yet have different hash codes: this happens
   * when one of them declares a type parameter whose leftmost bound is not its canonically first bound, and the other one spells
   * that leftmost bound out explicitly. Such pairs are rare enough to prefer a well-distributed hash code over finding them in an
   * erased signature map.
   */
  public static int erasedSignatureHashCode(@NotNull MethodSignature signature) {
    // MethodSignatureBase caches the very same hash code
    return signature instanceof MethodSignatureBase ? signature.hashCode() : calcErasedSignatureHashCode(signature);
  }

  static int calcErasedSignatureHashCode(@NotNull MethodSignature signature) {
    PsiType[] erasedTypes = hasIntersectionBound(signature)
                            ? calcCanonicalErasedParameterTypes(signature) : getErasedParameterTypes(signature);
    int hash = signature.getName().hashCode();
    hash = 31 * hash + erasedTypes.length;
    for (int i = 0, length = Math.min(3, erasedTypes.length); i < length; i++) {
      PsiType type = erasedTypes[i];
      if (type == null) continue;
      hash = 31 * hash + type.hashCode();
    }
    return hash;
  }

  private static boolean hasIntersectionBound(@NotNull MethodSignature signature) {
    for (PsiTypeParameter typeParameter : signature.getTypeParameters()) {
      if (typeParameter.getExtendsListTypes().length > 1) return true;
    }
    return false;
  }

  /**
   * @return the erased parameter types of the signature in which the erasure of a type variable declared by the signature with an
   * intersection bound is replaced by the erasure of its canonically first bound. Unlike the leftmost bound, that one does not
   * depend on the order in which the bounds are declared, hence it is the same for signature-equal methods.
   */
  private static PsiType @NotNull [] calcCanonicalErasedParameterTypes(@NotNull MethodSignature signature) {
    PsiType[] parameterTypes = signature.getParameterTypes();
    if (parameterTypes.length == 0) return PsiType.EMPTY_ARRAY;

    PsiTypeParameter[] typeParameters = signature.getTypeParameters();
    PsiSubstitutor substitutor = signature.getSubstitutor();
    PsiType[] erasedTypes = PsiType.createArray(parameterTypes.length);
    for (int i = 0; i < parameterTypes.length; i++) {
      erasedTypes[i] = canonicalErasure(parameterTypes[i], substitutor, typeParameters);
    }
    return erasedTypes;
  }

  private static @Nullable PsiType canonicalErasure(@NotNull PsiType type,
                                                    @NotNull PsiSubstitutor substitutor,
                                                    PsiTypeParameter @NotNull [] typeParameters) {
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      PsiType erasedComponentType = canonicalErasure(componentType, substitutor, typeParameters);
      if (erasedComponentType == componentType) return type;
      return erasedComponentType == null ? null : erasedComponentType.createArrayType();
    }
    PsiTypeParameter typeParameter = resolveTypeParameter(type);
    if (typeParameter != null && ArrayUtil.contains(typeParameter, typeParameters)) {
      PsiClassType[] bounds = typeParameter.getExtendsListTypes();
      if (bounds.length > 1) {
        PsiType canonicalBound = null;
        String canonicalText = null;
        for (PsiClassType bound : bounds) {
          PsiType erasedBound = TypeConversionUtil.erasure(bound, substitutor);
          if (erasedBound == null) continue;
          String text = erasedBound.getCanonicalText();
          if (canonicalText == null || text.compareTo(canonicalText) < 0) {
            canonicalText = text;
            canonicalBound = erasedBound;
          }
        }
        if (canonicalBound != null) return canonicalBound;
      }
    }
    return TypeConversionUtil.erasure(type, substitutor);
  }

  private static PsiType @NotNull [] getErasedParameterTypes(@NotNull MethodSignature signature) {
    return signature instanceof MethodSignatureBase
           ? ((MethodSignatureBase)signature).getErasedParameterTypes() : calcErasedParameterTypes(signature);
  }

  public static PsiType @NotNull [] calcErasedParameterTypes(@NotNull MethodSignature signature) {
    PsiType[] parameterTypes = signature.getParameterTypes();
    if (parameterTypes.length == 0) return PsiType.EMPTY_ARRAY;

    PsiSubstitutor substitutor = signature.getSubstitutor();
    PsiType[] erasedTypes = PsiType.createArray(parameterTypes.length);
    for (int i = 0; i < parameterTypes.length; i++) {
      erasedTypes[i] = TypeConversionUtil.erasure(parameterTypes[i], substitutor);
    }
    return erasedTypes;
  }

  public static @NotNull MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                               @Nullable PsiParameterList parameterTypes,
                                                               @Nullable PsiTypeParameterList typeParameterList,
                                                               @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  public static @NotNull MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                               @Nullable PsiParameterList parameterTypes,
                                                               @Nullable PsiTypeParameterList typeParameterList,
                                                               @NotNull PsiSubstitutor substitutor,
                                                               boolean isConstructor) {
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor, isConstructor);
  }

  public static @NotNull MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                               PsiType @NotNull [] parameterTypes,
                                                               PsiTypeParameter @NotNull [] typeParameterList,
                                                               @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  public static @NotNull MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                               PsiType @NotNull [] parameterTypes,
                                                               PsiTypeParameter @NotNull [] typeParameterList,
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
    // Signatures may be equal (JLS 8.4.2) even when their erasures are not, see areErasedParametersEqual; otherwise unequal
    // erasures are a cheap way to tell the signatures apart.
    PsiType[] subErased = getErasedParameterTypes(subSignature);
    PsiType[] superErased = getErasedParameterTypes(superSignature);
    if (!Arrays.equals(subErased, superErased) &&
        !differOnlyInTypeParameterBoundsOrder(subSignature, superSignature, subErased, superErased)) {
      return false;
    }

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
    if (superMethodCandidate.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
        !JavaPsiFacade.getInstance(derivedClass.getProject()).arePackagesTheSame(superClassCandidate, derivedClass)) {
      return false;
    }
    final PsiSubstitutor superSubstitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(superClassCandidate, derivedClass,
                                                                                             PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return false;
    final MethodSignature superSignature = superMethodCandidate.getSignature(superSubstitutor);
    final MethodSignature derivedSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
    return isSubsignature(superSignature, derivedSignature);
  }

  public static @Nullable PsiMethod findMethodInSuperClassBySignatureInDerived(final @NotNull PsiClass aClass,
                                                                               final @NotNull PsiClass superClass,
                                                                               @NotNull MethodSignature signature,
                                                                               final boolean checkDeep) {
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
    return doFindMethodInSuperClassBySignatureInDerived(superClass, superSubstitutor, signature, checkDeep);
  }

  private static @Nullable PsiMethod doFindMethodInSuperClassBySignatureInDerived(@NotNull PsiClass superClass,
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

  public static @Nullable PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod pattenMethod, boolean checkBases) {
    return findMethodBySignature(aClass, pattenMethod.getSignature(PsiSubstitutor.EMPTY), checkBases);
  }

  public static @Nullable PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull MethodSignature methodSignature, boolean checkBases) {
    String name = methodSignature.isConstructor() ? aClass.getName() : methodSignature.getName();
    if (name == null) return null;
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(name, checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (methodSignature.equals(foundMethodSignature)) return method;
    }
    return null;
  }

  public static @Nullable PsiMethod findMethodBySuperSignature(@NotNull PsiClass aClass, @NotNull MethodSignature methodSignature, final boolean checkBases) {
    String name = methodSignature.isConstructor() ? aClass.getName() : methodSignature.getName();
    if (name == null) return null;
    List<Pair<PsiMethod, PsiSubstitutor>> pairs = aClass.findMethodsAndTheirSubstitutorsByName(name, checkBases);
    for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
      PsiMethod method = pair.first;
      PsiSubstitutor substitutor = pair.second;
      MethodSignature foundMethodSignature = method.getSignature(substitutor);
      if (isSubsignature(methodSignature, foundMethodSignature)) return method;
    }
    return null;
  }

  public static @Nullable PsiMethod findMethodBySuperMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method, final boolean checkBases) {
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

  private static PsiMethod @NotNull [] getOverloads(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    return aClass.findMethodsByName(method.getName(), false);
  }

  public static boolean areParametersErasureEqual(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    if (method1.getParameterList().getParametersCount() != method2.getParameterList().getParametersCount()) return false;
    return areSignaturesErasureEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesErasureEqual(@NotNull MethodSignature signature1, @NotNull MethodSignature signature2) {
    return METHOD_PARAMETERS_ERASURE_STRATEGY.equals(signature1, signature2);
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
  public static @Nullable PsiSubstitutor getSuperMethodSignatureSubstitutor(@NotNull MethodSignature methodSignature, @NotNull MethodSignature superMethodSignature) {
    PsiTypeParameter[] methodTypeParameters = methodSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameters = superMethodSignature.getTypeParameters();

    // both methods are parameterized and number of parameters mismatch
    if (methodTypeParameters.length != superTypeParameters.length) return null;

    PsiSubstitutor result = superMethodSignature.getSubstitutor();
    for (int i = 0; i < methodTypeParameters.length; i++) {
      PsiTypeParameter methodTypeParameter = methodTypeParameters[i];
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(methodTypeParameter.getProject());
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

  public static @NotNull PsiSubstitutor combineSubstitutors(@NotNull PsiSubstitutor substitutor1, @NotNull PsiSubstitutor substitutor2) {
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

  public static PsiMethod @NotNull [] convertMethodSignaturesToMethods(@NotNull List<? extends MethodSignatureBackedByPsiMethod> sameNameMethodList) {
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
    if (PsiTypes.voidType().equals(r1)) {
      return PsiTypes.voidType().equals(r2);
    }

    //If R1 is a primitive type then R2 is identical to R1.
    if (r1 instanceof PsiPrimitiveType) {
      return r1.equals(r2);
    }

    if ((r1 instanceof PsiClassType || r1 instanceof PsiArrayType) && r2 != null) {

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
