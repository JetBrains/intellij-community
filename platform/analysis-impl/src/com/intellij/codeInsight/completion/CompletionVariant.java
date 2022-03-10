// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @deprecated use CompletionContributor
 */
@Deprecated(forRemoval = true)
public class CompletionVariant {
  protected static final TailType DEFAULT_TAIL_TYPE = TailType.SPACE;

  private final Set<Scope> myScopeClasses = new HashSet<>();
  private ElementFilter myPosition;
  private final List<CompletionVariantItem> myCompletionsList = new ArrayList<>();
  private InsertHandler myInsertHandler = null;
  private final Map<Object, Object> myItemProperties = new HashMap<>();

  public CompletionVariant() {
  }

  public CompletionVariant(Class scopeClass, ElementFilter position){
    includeScopeClass(scopeClass);
    myPosition = position;
  }

  public CompletionVariant(ElementFilter position){
    myPosition = position;
  }

  boolean isScopeAcceptable(PsiElement scope){
    return isScopeClassAcceptable(scope.getClass());
  }

  boolean isScopeFinal(PsiElement scope){
    return isScopeClassFinal(scope.getClass());
  }

  InsertHandler getInsertHandler(){
    return myInsertHandler;
  }

  public void setInsertHandler(InsertHandler handler){
    myInsertHandler = handler;
  }

  Map<Object, Object> getItemProperties() {
    return myItemProperties;
  }

  private boolean isScopeClassFinal(Class<?> scopeClass){
    for (final Scope myScopeClass : myScopeClasses) {
      if (ReflectionUtil.isAssignable(myScopeClass.myClass, scopeClass) && myScopeClass.myIsFinalScope) {
        return true;
      }
    }
    return false;
  }

  private boolean isScopeClassAcceptable(Class<?> scopeClass){
    boolean ret = false;

    for (final Scope myScopeClass : myScopeClasses) {
      final Class<?> aClass = myScopeClass.myClass;
      if (ReflectionUtil.isAssignable(aClass, scopeClass)) {
        ret = true;
        break;
      }
    }

    return ret;
  }

  public void includeScopeClass(Class<?> aClass){
    myScopeClasses.add(new Scope(aClass, false));
  }

  public void includeScopeClass(Class<?> aClass, boolean isFinalScope){
    myScopeClasses.add(new Scope(aClass, isFinalScope));
  }

  public void addCompletionFilter(ElementFilter filter, TailType tailType){
    addCompletion(filter, tailType);
  }

  public void addCompletionFilter(ElementFilter filter){
    addCompletionFilter(filter, TailType.NONE);
  }

  public void addCompletion(@NonNls String keyword){
    addCompletion(keyword, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(@NonNls String keyword, TailType tailType){
    addCompletion((Object)keyword, tailType);
  }

  private void addCompletion(Object completion, TailType tail){
    myCompletionsList.add(new CompletionVariantItem(completion, tail));
  }

  boolean isVariantApplicable(PsiElement position, PsiElement scope){
    return isScopeAcceptable(scope) && myPosition.isAcceptable(position, scope);
  }

  void addReferenceCompletions(PsiReference reference, PsiElement position, Set<? super LookupElement> set, final PsiFile file,
                               final CompletionData completionData){
    for (final CompletionVariantItem ce : myCompletionsList) {
      if(ce.myCompletion instanceof ElementFilter){
        final ElementFilter filter = (ElementFilter)ce.myCompletion;
        completionData.completeReference(reference, position, set, ce.myTailType, filter, this);
      }
    }
  }

  void addKeywords(Set<LookupElement> set, final CompletionData completionData) {
    for (final CompletionVariantItem ce : myCompletionsList) {
      completionData.addKeywords(set, this, ce.myCompletion, ce.myTailType);
    }
  }

  boolean hasReferenceFilter(){
    for (final CompletionVariantItem item: myCompletionsList) {
      if (item.myCompletion instanceof ElementFilter) {
        return true;
      }
    }
    return false;
  }

  boolean hasKeywordCompletions(){
    for (final CompletionVariantItem item : myCompletionsList) {
      if (!(item.myCompletion instanceof ElementFilter)) {
        return true;
      }
    }
    return false;
  }


  private static class Scope{
    Class<?> myClass;
    boolean myIsFinalScope;

    Scope(Class<?> aClass, boolean isFinalScope){
      myClass = aClass;
      myIsFinalScope = isFinalScope;
    }
  }

  private static class CompletionVariantItem{
    public Object myCompletion;
    public TailType myTailType;

    CompletionVariantItem(Object completion, TailType tailtype){
      myCompletion = completion;
      myTailType = tailtype;
    }

    public String toString(){
      return myCompletion.toString();
    }
  }

  public String toString(){
    return "completion variant at " + myPosition.toString() + " completions: " + myCompletionsList;
  }

  public void setCaseInsensitive(boolean caseInsensitive) {
    myItemProperties.put(LookupItem.CASE_INSENSITIVE, caseInsensitive);
  }

}
