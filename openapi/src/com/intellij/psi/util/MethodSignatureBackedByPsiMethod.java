/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;

import java.util.Iterator;

public class MethodSignatureBackedByPsiMethod extends MethodSignatureBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureBackedByPsiMethod");

  private final PsiMethod myMethod;

  public MethodSignatureBackedByPsiMethod(PsiMethod method, PsiSubstitutor substitutor) {
    super(substitutor, method.getParameterList(), method.getTypeParameterList());
    LOG.assertTrue(method.isValid());
    myMethod = method;
  }

  public String getName() {
    return myMethod.getName();
  }


  public boolean isRaw() {
    return isRawSubstitutorForMethod(myMethod, getSubstitutor());
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

  private static boolean isRawSubstitutorForMethod(PsiMethod method, PsiSubstitutor substitutor) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(method);
    while (iterator.hasNext()) {
      final PsiTypeParameter typeParameter = iterator.next();
      if (substitutor.substitute(typeParameter) == null) return true;
    }
    return false;
  }
}
