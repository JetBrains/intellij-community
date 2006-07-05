package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:33:24
 * To change this template use Options | File Templates.
 */
public abstract class GenericReference implements PsiReference, EmptyResolveMessageProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.GenericReference");
  public static final GenericReference[] EMPTY_ARRAY = new GenericReference[0];
  @Nullable
  private final PsiReferenceProvider myProvider;

  public boolean isSoft(){
    return false;
  }

  public GenericReference(final PsiReferenceProvider provider){
    myProvider = provider;
  }

  public PsiElement resolve(){
    final PsiManager manager = getElement().getManager();
    if(manager instanceof PsiManagerImpl){
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
    }
    return resolveInner();
  }

  @Nullable
  public PsiElement resolveInner(){
    final List resultSet = new ArrayList();
    final ConflictFilterProcessor processor;
    try{
      processor = ProcessorRegistry.getProcessorByType(getType(), resultSet, needToCheckAccessibility() ? getElement() : null);
      processor.setName(getCanonicalText());
    }
    catch(ProcessorRegistry.IncompatibleReferenceTypeException e){
      LOG.error(e);
      return null;
    }

    processVariants(processor);
    final JavaResolveResult[] result = processor.getResult();
    if(result.length != 1) return null;
    return result[0].getElement();
  }

  public boolean isReferenceTo(final PsiElement element){
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants(){
    final List ret = new ArrayList();
    final FilterScopeProcessor proc;
    try{
      proc = ProcessorRegistry.getProcessorByType(getSoftenType(), ret, needToCheckAccessibility() ? getElement() : null);
    }
    catch(ProcessorRegistry.IncompatibleReferenceTypeException e){
      LOG.error(e);
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    processVariants(proc);

    return ret.toArray();
  }

  public void processVariants(final PsiScopeProcessor processor){
    final PsiElement context = getContext();
    if(context != null){
      PsiScopesUtil.processScope(context, processor, PsiSubstitutor.EMPTY, getElement(), getElement());
    }
    else if(getContextReference() == null && myProvider != null){
      myProvider.handleEmptyContext(processor, getElement());
    }
  }

  @Nullable
  protected static <T extends PsiElement> ElementManipulator<T> getManipulator(T currentElement){
    return ReferenceProvidersRegistry.getInstance(currentElement.getProject()).getManipulator(currentElement);
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


  public String getUnresolvedMessagePattern(){
    final ReferenceType type = getType();
    if (type != null) return type.getUnresolvedMessage();
    return CodeInsightBundle.message("error.cannot.resolve.default.message");
  }

  public abstract PsiElement getContext();
  public abstract PsiReference getContextReference();
  public abstract ReferenceType getType();
  public abstract ReferenceType getSoftenType();
  public abstract boolean needToCheckAccessibility();

  private static class MyResolver implements ResolveCache.Resolver {
    static MyResolver INSTANCE = new MyResolver();
    @Nullable
    public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
      return ((GenericReference)ref).resolveInner();
    }
  }
}
