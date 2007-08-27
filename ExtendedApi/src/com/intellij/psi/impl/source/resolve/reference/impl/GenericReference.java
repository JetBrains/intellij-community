package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
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
public abstract class GenericReference extends CachingReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.GenericReference");
  public static final GenericReference[] EMPTY_ARRAY = new GenericReference[0];

  @Nullable
  private final PsiReferenceProvider myProvider;

  public GenericReference(final PsiReferenceProvider provider){
    myProvider = provider;
  }

  @Nullable
  public PsiElement resolveInner(){
    final List<CandidateInfo> resultSet = new ArrayList<CandidateInfo>();
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
    final List<CandidateInfo> ret = new ArrayList<CandidateInfo>();
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


  public String getUnresolvedMessagePattern(){
    final ReferenceType type = getType();
    if (type != null) return type.getUnresolvedMessage();
    return CodeInsightBundle.message("error.cannot.resolve.default.message");
  }

  @Nullable
  public abstract PsiElement getContext();
  @Nullable
  public abstract PsiReference getContextReference();
  public abstract ReferenceType getType();
  public abstract ReferenceType getSoftenType();
  public abstract boolean needToCheckAccessibility();

}
