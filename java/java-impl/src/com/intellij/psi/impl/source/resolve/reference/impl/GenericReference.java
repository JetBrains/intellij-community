// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  private final GenericReferenceProvider myProvider;

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
  @Nullable
  public PsiElement handleElementRename(@NotNull String string) throws IncorrectOperationException {
    final PsiElement element = getElement();
    ElementManipulator<PsiElement> man = ElementManipulators.getManipulator(element);
    if (man != null) {
      return man.handleContentChange(element, getRangeInElement(), string);
    }
    return element;
  }

  @Nullable
  public PsiReferenceProvider getProvider() {
    return myProvider;
  }

  @Nullable
  public abstract PsiElement getContext();

  @Nullable
  public abstract PsiReference getContextReference();
}
