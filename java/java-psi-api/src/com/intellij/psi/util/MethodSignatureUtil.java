/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
        int result = signature.isConstructor() ? 0 : signature.getName().hashCode();

        PsiType[] parameterTypes = signature.getParameterTypes();
        result += 37 * parameterTypes.length;
        PsiType firstParamType = parameterTypes.length == 0 ? null : parameterTypes[0];
        if (firstParamType != null) {
          firstParamType = TypeConversionUtil.erasure(firstParamType, signature.getSubstitutor());
          assert firstParamType != null : parameterTypes[0];
          result = 31*result + firstParamType.hashCode();
        }
        return result;
      }

      @Override
      public boolean equals(MethodSignature method1, MethodSignature method2) {
        if (method1.isConstructor() != method2.isConstructor()) return false;
        if (!method1.isConstructor() && !method1.getName().equals(method2.getName())) return false;
        final PsiType[] parameterTypes1 = method1.getParameterTypes();
        final PsiType[] parameterTypes2 = method2.getParameterTypes();
        if (parameterTypes1.length != parameterTypes2.length) return false;

        final PsiSubstitutor substitutor1 = method1.getSubstitutor();
        final PsiSubstitutor substitutor2 = method2.getSubstitutor();
        for (int i = 0; i < parameterTypes1.length; i++) {
          final PsiType type1 = TypeConversionUtil.erasure(substitutor1.substitute(parameterTypes1[i]), substitutor1);
          final PsiType type2 = TypeConversionUtil.erasure(substitutor2.substitute(parameterTypes2[i]), substitutor2);
          if (!Comparing.equal(type1, type2)) return false;
        }
        return true;
      }
    };

  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @Nullable PsiParameterList parameterTypes,
                                                      @Nullable PsiTypeParameterList typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @Nullable PsiParameterList parameterTypes,
                                                      @Nullable PsiTypeParameterList typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      boolean isConstructor) {
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor, isConstructor);
  }

  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @NotNull PsiType[] parameterTypes,
                                                      @NotNull PsiTypeParameter[] typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor) {
    return createMethodSignature(name, parameterTypes, typeParameterList, substitutor, false);
  }

  public static MethodSignature createMethodSignature(@NonNls @NotNull String name,
                                                      @NotNull PsiType[] parameterTypes,
                                                      @NotNull PsiTypeParameter[] typeParameterList,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      boolean isConstructor) {
    return new MethodSignatureHandMade(name, parameterTypes, typeParameterList, substitutor, isConstructor);
  }

  public static boolean areSignaturesEqual(PsiMethod method1, PsiMethod method2) {
    return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesEqual(MethodSignature method1, MethodSignature method2) {
    if (method2 == method1) return true;
    if (!areSignaturesEqualLightweight(method1, method2)) return false;
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(method1, method2);
    return checkSignaturesEqualInner(method1, method2, unifyingSubstitutor);
  }

  private static boolean checkSignaturesEqualInner(final MethodSignature subSignature,
                                                   final MethodSignature superSignature,
                                                   final PsiSubstitutor unifyingSubstitutor) {
    if (unifyingSubstitutor == null) return false;
    if (!METHOD_PARAMETERS_ERASURE_EQUALITY.equals(subSignature, superSignature)) return false;

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

  public static boolean areSignaturesEqualLightweight(final MethodSignature sig1, final MethodSignature sig2) {
    final boolean isConstructor1 = sig1.isConstructor();
    final boolean isConstructor2 = sig2.isConstructor();
    if (isConstructor1 != isConstructor2) return false;

    if (!isConstructor1 && !isConstructor2 || !(sig1 instanceof HierarchicalMethodSignature || sig2 instanceof HierarchicalMethodSignature)) {
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

  public static boolean isSuperMethod(final PsiMethod superMethodCandidate, final PsiMethod derivedMethod) {
    PsiClass superClassCandidate = superMethodCandidate.getContainingClass();
    PsiClass derivedClass = derivedMethod.getContainingClass();
    if (derivedClass == null || superClassCandidate == null) return false;
    if (!derivedClass.isInheritor(superClassCandidate, true)) return false;
    final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClassCandidate, derivedClass,
                                                                                        PsiSubstitutor.EMPTY);
    final MethodSignature superSignature = superMethodCandidate.getSignature(superSubstitutor);
    final MethodSignature derivedSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
    return isSubsignature(superSignature, derivedSignature);
  }

  @Nullable
  public static PsiMethod findMethodInSuperClassBySignatureInDerived(@NotNull final PsiClass aClass,
                                                                     @NotNull final PsiClass superClass,
                                                                     final MethodSignature signature,
                                                                     final boolean checkDeep) {
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
    return doFindMethodInSuperClassBySignatureInDerived(superClass, superSubstitutor, signature, checkDeep);
  }

  @Nullable
  private static PsiMethod doFindMethodInSuperClassBySignatureInDerived(final PsiClass superClass,
                                                                        final PsiSubstitutor superSubstitutor,
                                                                        final MethodSignature signature,
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
  public static PsiMethod findMethodBySignature(final PsiClass aClass, final PsiMethod pattenMethod, boolean checkBases) {
    return findMethodBySignature(aClass, pattenMethod.getSignature(PsiSubstitutor.EMPTY), checkBases);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(final PsiClass aClass, final MethodSignature methodSignature, boolean checkBases) {
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
  public static PsiMethod findMethodBySuperSignature(final PsiClass aClass, final MethodSignature methodSignature, final boolean checkBases) {
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
  public static PsiMethod findMethodBySuperMethod(final PsiClass aClass, final PsiMethod method, final boolean checkBases) {
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

  public static boolean hasOverloads(PsiMethod method) {
    return getOverloads(method).length > 1;
  }

  public static PsiMethod[] getOverloads(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    return aClass.findMethodsByName(method.getName(), false);
  }

  public static boolean areParametersErasureEqual(PsiMethod method1, PsiMethod method2) {
    if (method1.getParameterList().getParametersCount() != method2.getParameterList().getParametersCount()) return false;
    return areSignaturesErasureEqual(method1.getSignature(PsiSubstitutor.EMPTY), method2.getSignature(PsiSubstitutor.EMPTY));
  }

  public static boolean areSignaturesErasureEqual(MethodSignature signature1, MethodSignature signature2) {
    return METHOD_PARAMETERS_ERASURE_EQUALITY.equals(signature1, signature2);
  }

  /**
   * @param methodSignature method signature
   * @param superMethodSignature super method signature
   * @return null if signatures do not match
   */
  @Nullable
  public static PsiSubstitutor getSuperMethodSignatureSubstitutor(MethodSignature methodSignature, MethodSignature superMethodSignature) {
    PsiSubstitutor result = getSuperMethodSignatureSubstitutorImpl(methodSignature, superMethodSignature);
    if (result == null) return null;

    PsiTypeParameter[] methodTypeParameters = methodSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameters = superMethodSignature.getTypeParameters();
    PsiSubstitutor methodSubstitutor = methodSignature.getSubstitutor();

    //check bounds
    for (int i = 0; i < methodTypeParameters.length; i++) {
      PsiTypeParameter methodTypeParameter = methodTypeParameters[i];
      PsiTypeParameter superTypeParameter = superTypeParameters[i];
      final Set<PsiType> methodSupers = new HashSet<PsiType>();
      for (PsiClassType methodSuper : methodTypeParameter.getSuperTypes()) {
        methodSupers.add(methodSubstitutor.substitute(methodSuper));
      }

      final Set<PsiType> superSupers = new HashSet<PsiType>();
      for (PsiClassType superSuper : superTypeParameter.getSuperTypes()) {
        superSupers.add(methodSubstitutor.substitute(PsiUtil.captureToplevelWildcards(result.substitute(superSuper), methodTypeParameter)));
      }
      methodSupers.remove(PsiType.getJavaLangObject(methodTypeParameter.getManager(), methodTypeParameter.getResolveScope()));
      superSupers.remove(PsiType.getJavaLangObject(superTypeParameter.getManager(), superTypeParameter.getResolveScope()));
      if (!methodSupers.equals(superSupers)) return null;
    }

    return result;
  }

  @Nullable
  private static PsiSubstitutor getSuperMethodSignatureSubstitutorImpl(MethodSignature methodSignature, MethodSignature superSignature) {
    // normalize generic method declarations: correlate type parameters
    // todo: correlate type params by name?
    PsiTypeParameter[] methodTypeParameters = methodSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameters = superSignature.getTypeParameters();

    // both methods are parameterized and number of parameters mismatch
    if (methodTypeParameters.length != superTypeParameters.length) return null;

    PsiSubstitutor result = superSignature.getSubstitutor();
    for (int i = 0; i < methodTypeParameters.length; i++) {
      PsiTypeParameter methodTypeParameter = methodTypeParameters[i];
      PsiElementFactory factory = JavaPsiFacade.getInstance(methodTypeParameter.getProject()).getElementFactory();
      result = result.put(superTypeParameters[i], factory.createType(methodTypeParameter));
    }

    return result;
  }

  public static PsiSubstitutor combineSubstitutors(PsiSubstitutor substitutor1, PsiSubstitutor substitutor2) {
    if (substitutor1 == PsiSubstitutor.EMPTY) return substitutor2;
    Set<PsiTypeParameter> parameters1 = substitutor1.getSubstitutionMap().keySet();
    final PsiTypeParameter[] typeParameters = parameters1.toArray(new PsiTypeParameter[parameters1.size()]);
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
  public static PsiMethod[] convertMethodSignaturesToMethods(List<? extends MethodSignatureBackedByPsiMethod> sameNameMethodList) {
    final PsiMethod[] methods = new PsiMethod[sameNameMethodList.size()];
    for (int i = 0; i < sameNameMethodList.size(); i++) {
      methods[i] = sameNameMethodList.get(i).getMethod();
    }
    return methods;
  }

  public static boolean isSubsignature(MethodSignature superSignature, MethodSignature subSignature) {
    if (subSignature == superSignature) return true;
    if (!areSignaturesEqualLightweight(superSignature, subSignature)) return false;
    PsiSubstitutor unifyingSubstitutor = getSuperMethodSignatureSubstitutor(subSignature, superSignature);
    if (checkSignaturesEqualInner(superSignature, subSignature, unifyingSubstitutor)) return true;

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
}
