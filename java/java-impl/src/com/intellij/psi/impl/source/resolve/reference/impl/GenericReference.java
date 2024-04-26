// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GenericReference extends CachingReference implements EmptyResolveMessageProvider {
  public static final GenericReference[] EMPTY_ARRAY = new GenericReference[0];

  private final @Nullable GenericReferenceProvider myProvider;

  public GenericReference(final GenericReferenceProvider provider) {
    myProvider = provider;
  }

  public void processVariants(final PsiScopeProcessor processor) {
    final PsiElement context = getContext();
    if (context != null) {
      context.processDeclarations(processor, ResolveState.initial(), getElement(), getElement());
    }
    else if (getContextReference() == null && myProvider != null) {
      myProvider.handleEmptyContext(processor, getElement());
    }
  }

  @Override
  public @Nullable PsiElement handleElementRename(@NotNull String string) throws IncorrectOperationException {
    final PsiElement element = getElement();
    ElementManipulator<PsiElement> man = ElementManipulators.getManipulator(element);
    if (man != null) {
      return man.handleContentChange(element, getRangeInElement(), string);
    }
    return element;
  }

  public @Nullable PsiReferenceProvider getProvider() {
    return myProvider;
  }

  public abstract @Nullable PsiElement getContext();

  public abstract @Nullable PsiReference getContextReference();
}
