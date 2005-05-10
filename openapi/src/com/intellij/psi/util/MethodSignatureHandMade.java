/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.*;

public class MethodSignatureHandMade extends MethodSignatureBase {
  private final String myName;
  MethodSignatureHandMade(String name, PsiParameterList parameterList, PsiTypeParameterList typeParameterList, PsiSubstitutor substitutor) {
    super(substitutor, parameterList, typeParameterList);
    myName = name;
  }

  MethodSignatureHandMade(String name, PsiType[] parameterTypes, PsiTypeParameter[] typeParameters, PsiSubstitutor substitutor) {
    super(substitutor, parameterTypes, typeParameters);
    myName = name;
  }


  public String getName() {
    return myName;
  }

  public boolean isRaw() {
    for (final PsiTypeParameter typeParameter : myTypeParameters) {
      if (getSubstitutor().substitute(typeParameter) == null) return true;
    }
    return false;
  }

  public boolean isInGenericContext() {
    return !isRaw();
  }

}
