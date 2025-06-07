// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class MethodSignatureBackedByPsiMethod extends MethodSignatureBase {
  private final PsiMethod myMethod;
  private final boolean myIsRaw;
  private final String myName;

  protected MethodSignatureBackedByPsiMethod(@NotNull PsiMethod method,
                                             @NotNull PsiSubstitutor substitutor,
                                             boolean isRaw,
                                             PsiType @NotNull [] parameterTypes,
                                             PsiTypeParameter @NotNull [] methodTypeParameters) {
    super(substitutor, parameterTypes, methodTypeParameters);
    myIsRaw = isRaw;
    myMethod = method;
    myName = method.getName();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean isRaw() {
    return myIsRaw;
  }

  @Override
  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MethodSignatureBackedByPsiMethod){ // optimization
      if (((MethodSignatureBackedByPsiMethod)o).myMethod == myMethod) return true;
    }

    return super.equals(o);
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public static @NotNull MethodSignatureBackedByPsiMethod create(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return create(method, substitutor, PsiUtil.isRawSubstitutor(method, substitutor));
  }

  public static @NotNull MethodSignatureBackedByPsiMethod create(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor, boolean isRaw) {
    PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
    if (isRaw) {
      substitutor = JavaPsiFacade.getElementFactory(method.getProject()).createRawSubstitutor(substitutor, methodTypeParameters);
      methodTypeParameters = PsiTypeParameter.EMPTY_ARRAY;
    }

    try {
      substitutor.ensureValid();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      throw PluginException.createByClass(e, method.getClass());
    }

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] parameterTypes = PsiType.createArray(parameters.length);
    for (int i = 0; i < parameterTypes.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiType type = parameter.getType();
      parameterTypes[i] = isRaw ? TypeConversionUtil.erasure(substitutor.substitute(type)) : type;
    }

    return new MethodSignatureBackedByPsiMethod(method, substitutor, isRaw, parameterTypes, methodTypeParameters);
  }
}
