/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;

import java.util.Arrays;

public abstract class MethodSignatureBase implements MethodSignature {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureBase");

  private final PsiSubstitutor mySubstitutor;
  protected final PsiType[] myParameterTypes;
  protected final PsiTypeParameter[] myTypeParameters;

  protected MethodSignatureBase(PsiSubstitutor substitutor, PsiType[] parameterTypes, PsiTypeParameter[] typeParameters) {
    mySubstitutor = substitutor;
    if (parameterTypes == null) {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    } else {
      myParameterTypes = new PsiType[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        PsiType type = parameterTypes[i];
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }
    myTypeParameters = typeParameters == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameters;
  }

  protected MethodSignatureBase(PsiSubstitutor substitutor, PsiParameterList parameterList, PsiTypeParameterList typeParameterList) {
    LOG.assertTrue(substitutor != null);
    mySubstitutor = substitutor;
    if (parameterList != null) {
      final PsiParameter[] parameters = parameterList.getParameters();
      myParameterTypes = new PsiType[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        PsiType type = parameters[i].getType();
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }
    else {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    }

    myTypeParameters = typeParameterList == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameterList.getTypeParameters();
  }

  public PsiType[] getParameterTypes() {
    return myParameterTypes;
  }

  public PsiTypeParameter[] getTypeParameters() {
    return myTypeParameters;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodSignature)) return false;

    final MethodSignature methodSignature = (MethodSignature)o;
    return MethodSignatureUtil.areSignaturesEqual(methodSignature, this);
  }

  public int hashCode() {
    int result = getName().hashCode();

    PsiType[] parameterTypes = getParameterTypes();
    result += 37 * parameterTypes.length;
    PsiType firstParamType = parameterTypes.length != 0 ? parameterTypes[0] : null;
    if (firstParamType != null) {
      if (getTypeParameters().length > 0) {
        firstParamType = TypeConversionUtil.erasure(firstParamType);
      }
      result += firstParamType.hashCode();
    }
    return result;
  }

  public String toString() {
    String s = "MethodSignature: ";
    final PsiTypeParameter[] typeParameters = getTypeParameters();
    if (typeParameters.length != 0) {
      String sep = "<";
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        s += sep + typeParameter.getName();
        sep = ", ";
      }
      s += ">";
    }
    s += getName() + "(" + Arrays.asList(getParameterTypes()) + ")";
    return s;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
