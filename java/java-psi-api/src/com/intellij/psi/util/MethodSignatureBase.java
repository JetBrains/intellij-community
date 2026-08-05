// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class MethodSignatureBase implements MethodSignature {

  private final PsiSubstitutor mySubstitutor;
  private final PsiType[] myParameterTypes;
  private volatile PsiType[] myErasedParameterTypes;
  final PsiTypeParameter[] myTypeParameters;
  private transient int myHash;

  MethodSignatureBase(@NotNull PsiSubstitutor substitutor, PsiType @NotNull [] parameterTypes, PsiTypeParameter @NotNull [] typeParameters) {
    mySubstitutor = substitutor;
    if (!substitutor.isValid()) throw new IllegalStateException("Substitutor " + substitutor + " is not valid");
    myParameterTypes = PsiType.createArray(parameterTypes.length);
    for (int i = 0; i < parameterTypes.length; i++) {
      PsiType type = parameterTypes[i];
      if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType) type).toArrayType();
      myParameterTypes[i] = substitutor.substitute(type);
    }
    myTypeParameters = typeParameters;
  }

  MethodSignatureBase(@NotNull PsiSubstitutor substitutor,
                      @Nullable PsiParameterList parameterList,
                      @Nullable PsiTypeParameterList typeParameterList) {
    mySubstitutor = substitutor;
    if (parameterList == null) {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    }
    else {
      final PsiParameter[] parameters = parameterList.getParameters();
      myParameterTypes = PsiType.createArray(parameters.length);
      for (int i = 0; i < parameters.length; i++) {
        PsiType type = parameters[i].getType();
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }

    myTypeParameters = typeParameterList == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameterList.getTypeParameters();
  }

  @Override
  public PsiType @NotNull [] getParameterTypes() {
    return myParameterTypes;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return myTypeParameters;
  }

  public PsiType @NotNull [] getErasedParameterTypes() {
    PsiType[] result = myErasedParameterTypes;
    if (result == null) {
      myErasedParameterTypes = result = MethodSignatureUtil.calcErasedParameterTypes(this);
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodSignature)) return false;

    final MethodSignature methodSignature = (MethodSignature)o;
    return MethodSignatureUtil.areSignaturesEqual(methodSignature, this);
  }

  @Override
  public int hashCode() {
    int hash = myHash;
    if (hash == 0) {
      // Erased parameter types must not contribute to the hash code when the erasure of the signature depends on the order in
      // which the bounds of its type parameters are declared: the erasure of a type variable is the erasure of its leftmost
      // bound (JLS 4.6), while that order does not affect signature equality (JLS 8.4.4).
      // Primitive parameter types are always safe to use: equal signatures have identical primitive parameters,
      // see MethodSignatureUtil.areSignaturesEqualLightweight.
      final boolean unstableErasure = hasIntersectionBound();
      hash = getName().hashCode();
      final PsiType[] parameterTypes = unstableErasure ? getParameterTypes() : getErasedParameterTypes();
      hash = 31 * hash + parameterTypes.length;
      for (int i = 0, length = Math.min(3, parameterTypes.length); i < length; i++) {
        PsiType type = parameterTypes[i];
        if (type == null || unstableErasure && !(type instanceof PsiPrimitiveType)) continue;
        hash = 31 * hash + type.hashCode();
      }
      myHash = hash;
    }
    return hash;
  }

  private boolean hasIntersectionBound() {
    for (PsiTypeParameter typeParameter : myTypeParameters) {
      if (typeParameter.getExtendsListTypes().length > 1) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    String s = getClass().getSimpleName() + ": ";
    final PsiTypeParameter[] typeParameters = getTypeParameters();
    if (typeParameters.length != 0) {
      s += Arrays.stream(typeParameters).map(PsiTypeParameter::getName)
              .collect(Collectors.joining(", ", "<", ">"));
    }
    s += getName() + "(" + Arrays.asList(getParameterTypes()) + ")";
    return s;
  }

  @Override
  public @NotNull PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
