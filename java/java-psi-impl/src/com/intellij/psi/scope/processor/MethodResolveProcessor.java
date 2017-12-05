// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class MethodResolveProcessor implements PsiScopeProcessor, ElementClassHint, NameHint {

  private final String myNameHint;
  private final List<PsiMethod> myMethods = new ArrayList<>();

  public MethodResolveProcessor() {
    myNameHint = null;
  }

  public MethodResolveProcessor(final String name) {
    myNameHint = name;
  }

  public PsiMethod[] getMethods() {
    return myMethods.toArray(new PsiMethod[myMethods.size()]);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiMethod) {
      ContainerUtil.addIfNotNull(myMethods, (PsiMethod)element);
    }
    return true;
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    if (hintKey == NameHint.KEY && myNameHint != null) {
      return (T)this;
    }
    return null;
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.METHOD;
  }

  public static PsiMethod[] findMethod(PsiClass psiClass, String methodName) {
    MethodResolveProcessor processor = new MethodResolveProcessor(methodName);
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }

  public static PsiMethod[] getAllMethods(PsiClass psiClass) {
    MethodResolveProcessor processor = new MethodResolveProcessor();
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }


  @Nullable
  @Override
  public String getName(@NotNull ResolveState state) {
    return myNameHint;
  }
}
