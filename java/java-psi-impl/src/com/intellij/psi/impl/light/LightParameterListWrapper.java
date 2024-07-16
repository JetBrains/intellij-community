// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class LightParameterListWrapper extends LightElement implements PsiParameterList, PsiMirrorElement {

  private final @NotNull PsiParameterList myDelegate;
  private final @NotNull PsiSubstitutor mySubstitutor;

  public LightParameterListWrapper(@NotNull PsiParameterList delegate, @NotNull PsiSubstitutor substitutor) {
    super(delegate.getManager(), delegate.getLanguage());
    myDelegate = delegate;
    mySubstitutor = substitutor;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiParameterList getPrototype() {
    return myDelegate;
  }

  @Override
  public PsiParameter @NotNull [] getParameters() {
    return mySubstitutor == PsiSubstitutor.EMPTY
           ? myDelegate.getParameters()
           : ContainerUtil.map2Array(myDelegate.getParameters(), PsiParameter.class,
                                     parameter -> new LightParameterWrapper(parameter, mySubstitutor));
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    if (parameter instanceof LightParameterWrapper) {
      parameter = ((LightParameterWrapper)parameter).getPrototype();
    }
    return myDelegate.getParameterIndex(parameter);
  }

  @Override
  public int getParametersCount() {
    return myDelegate.getParametersCount();
  }

  @Override
  public String toString() {
    return "List PSI parameter list wrapper: " + myDelegate;
  }
}
