package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.ElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
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
public abstract class GenericReference extends CachingReference {
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

  @Nullable
  public PsiElement handleElementRename(String string) throws IncorrectOperationException {
    final PsiElement element = getElement();
    if (element != null) {
      ElementManipulator<PsiElement> man = ReferenceProvidersRegistry.getInstance(element.getProject()).getManipulator(element);
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
