/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.psi.*;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:33:24
 * To change this template use Options | File Templates.
 */
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
  public PsiElement handleElementRename(String string) throws IncorrectOperationException {
    final PsiElement element = getElement();
    if (element != null) {
      ElementManipulator<PsiElement> man = ElementManipulators.getManipulator(element);
      if (man != null) {
        return man.handleContentChange(element, getRangeInElement(), string);
      }
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
