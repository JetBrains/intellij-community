/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.*;
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
    assert substitutor.isValid();
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

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodSignature)) return false;

    final MethodSignature methodSignature = (MethodSignature)o;
    return MethodSignatureUtil.areSignaturesEqual(methodSignature, this);
  }

  public int hashCode() {
    int hash = myHash;
    if (hash == 0) {
      hash = getName().hashCode();
      final PsiType[] parameterTypes = getErasedParameterTypes();
      hash = 31 * hash + parameterTypes.length;
      for (int i = 0, length = Math.min(3, parameterTypes.length); i < length; i++) {
        PsiType type = parameterTypes[i];
        if (type == null) continue;
        hash = 31 * hash + type.hashCode();
      }
      myHash = hash;
    }
    return hash;
  }

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
  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
