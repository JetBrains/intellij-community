/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;

public class MethodSignatureBackedByPsiMethod extends MethodSignatureBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureBackedByPsiMethod");

  private final PsiMethod myMethod;
  private final boolean myIsRaw;

  private MethodSignatureBackedByPsiMethod(PsiMethod method,
                                         PsiSubstitutor substitutor,
                                         boolean isRaw,
                                         PsiType[] parameterTypes,
                                         PsiTypeParameter[] methodTypeParameters) {
    super(substitutor, parameterTypes, methodTypeParameters);
    myIsRaw = isRaw;
    LOG.assertTrue(method.isValid());
    myMethod = method;
  }

  public String getName() {
    return myMethod.getName();
  }


  public boolean isRaw() {
    return myIsRaw;
  }

  public boolean equals(Object o) {
    if (o instanceof MethodSignatureBackedByPsiMethod){ // optimization
      if (((MethodSignatureBackedByPsiMethod)o).myMethod == myMethod) return true;
    }

    return super.equals(o);
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public static MethodSignatureBackedByPsiMethod create(PsiMethod method, PsiSubstitutor substitutor) {
    final boolean isRaw = PsiUtil.isRawSubstitutor(method, substitutor);
    PsiTypeParameter[] methodTypeParameters = method.getTypeParameterList().getTypeParameters();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] parameterTypes = new PsiType[parameters.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypes[i] = parameters[i].getType();
    }

    if (isRaw) {
      for (int i = 0; i < methodTypeParameters.length; i++) {
        substitutor = substitutor.put(methodTypeParameters[i], null);
      }
      methodTypeParameters = PsiTypeParameter.EMPTY_ARRAY;

      for (int i = 0; i < parameterTypes.length; i++) {
        parameterTypes[i] = TypeConversionUtil.erasure(parameterTypes[i]);
      }
    }
    return new MethodSignatureBackedByPsiMethod(method, substitutor, isRaw, parameterTypes, methodTypeParameters);
  }
}
