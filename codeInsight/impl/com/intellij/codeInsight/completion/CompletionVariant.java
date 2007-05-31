package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.io.Serializable;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.01.2003
 * Time: 17:38:14
 * To change this template use Options | File Templates.
 */

public class CompletionVariant {
  protected static final TailType DEFAULT_TAIL_TYPE = TailType.SPACE;

  private final Set<Scope> myScopeClasses = new HashSet<Scope>();
  private ElementFilter myPosition;
  private final List<CompletionVariantItem> myCompletionsList = new ArrayList<CompletionVariantItem>();
  private final Set<Class> myScopeClassExceptions = new HashSet<Class>();
  private InsertHandler myInsertHandler = null;
  private final Map<Object, Serializable> myItemProperties = new HashMap<Object, Serializable>();
  private boolean caseInsensitive;
  private final CompletionVariantPeer myPeer;

  public CompletionVariant() {
    myPeer = new CompletionVariantPeerImpl(this);
  }

  public CompletionVariant(Class scopeClass, ElementFilter position){
    includeScopeClass(scopeClass);
    if (scopeClass == PsiClass.class) {
      includeScopeClass(JspClassLevelDeclarationStatement.class); // hack for JSP completion
    }
    myPosition = position;
    myPeer = new CompletionVariantPeerImpl(this);
  }

  public CompletionVariant(ElementFilter position){
    myPosition = position;
    myPeer = new CompletionVariantPeerImpl(this);
  }

  public boolean isScopeAcceptable(PsiElement scope){
    return isScopeClassAcceptable(scope.getClass());
  }

  public boolean isScopeFinal(PsiElement scope){
    return isScopeClassFinal(scope.getClass());
  }

  public InsertHandler getInsertHandler(){
    return myInsertHandler;
  }

  public void setInsertHandler(InsertHandler handler){
    myInsertHandler = handler;
  }

  public void setItemProperty(Object id, Serializable value){
    myItemProperties.put(id, value);
  }

  public Map<Object, Serializable> getItemProperties() {
    return myItemProperties;
  }

  public boolean isScopeClassFinal(Class scopeClass){
    for (final Object myScopeClass : myScopeClasses) {
      Scope scope = (Scope)myScopeClass;
      if (ReflectionCache.isAssignable(scope.myClass, scopeClass) && scope.myIsFinalScope) {
        return true;
      }
    }
    return false;
  }

  public boolean isScopeClassAcceptable(Class scopeClass){
    boolean ret = false;

    for (final Object myScopeClass : myScopeClasses) {
      final Class aClass = ((Scope)myScopeClass).myClass;
      if (ReflectionCache.isAssignable(aClass, scopeClass)) {
        ret = true;
        break;
      }
    }

    if(ret){
      for (final Class aClass: myScopeClassExceptions) {
        if (ReflectionCache.isAssignable(aClass, scopeClass)) {
          ret = false;
          break;
        }
      }
    }
    return ret;
  }

  public void excludeScopeClass(Class<?> aClass){
    myScopeClassExceptions.add(aClass);
  }

  public void includeScopeClass(Class<?> aClass){
    myScopeClasses.add(new Scope(aClass, false));
  }

  public void includeScopeClass(Class<?> aClass, boolean isFinalScope){
    myScopeClasses.add(new Scope(aClass, isFinalScope));
  }

  public void addCompletionFilterOnElement(ElementFilter filter){
    addCompletionFilterOnElement(filter, TailType.NONE);
  }

  public void addCompletionFilterOnElement(ElementFilter filter, TailType tailType){
    addCompletion(new ElementExtractorFilter(filter), tailType);
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

  public void addCompletion(KeywordChooser chooser){
    addCompletion(chooser, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(KeywordChooser chooser, TailType tailType){
    addCompletion((Object)chooser, tailType);
  }

  public void addCompletion(ContextGetter chooser){
    addCompletion(chooser, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(ContextGetter chooser, TailType tailType){
    addCompletion((Object)chooser, tailType);
  }

  private void addCompletion(Object completion, TailType tail){
    myCompletionsList.add(new CompletionVariantItem(completion, tail));
  }

  public void addCompletion(@NonNls String[] keywordList){
    addCompletion(keywordList, DEFAULT_TAIL_TYPE);
  }

  public void addCompletion(String[] keywordList, TailType tailType){
    for (String aKeywordList : keywordList) {
      addCompletion(aKeywordList, tailType);
    }
  }

  public boolean isVariantApplicable(PsiElement position, PsiElement scope){
    return isScopeAcceptable(scope) && myPosition.isAcceptable(position, scope);
  }

  public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set, CompletionContext prefix){
    for (final CompletionVariantItem ce : myCompletionsList) {
      addReferenceCompletions(reference, position, set, prefix, ce);
    }
  }

  public void addKeywords(PsiElementFactory factory, Set<LookupItem> set, CompletionContext context, PsiElement position){
    for (final CompletionVariantItem ce : myCompletionsList) {
      final Object comp = ce.myCompletion;
      if (comp instanceof OffsetDependant) {
        ((OffsetDependant)comp).setOffset(context.startOffset);
      }

      if (comp instanceof String) {
        myPeer.addKeyword(factory, set, ce.myTailType, comp, context);
      }
      else if (comp instanceof ContextGetter) {
        final Object[] elements = ((ContextGetter)comp).get(position, context);
        for (Object element : elements) {
          myPeer.addLookupItem(set, ce.myTailType, element, context);
        }
      }
      // TODO: KeywordChooser -> ContextGetter
      else if (comp instanceof KeywordChooser) {
        final String[] keywords = ((KeywordChooser)comp).getKeywords(context, position);
        for (String keyword : keywords) {
          myPeer.addKeyword(factory, set, ce.myTailType, keyword, context);
        }
      }
    }
  }

  public boolean hasReferenceFilter(){
    for (final CompletionVariantItem item: myCompletionsList) {
      if (item.myCompletion instanceof ElementFilter) {
        return true;
      }
    }
    return false;
  }

  public boolean hasKeywordCompletions(){
    for (final CompletionVariantItem item : myCompletionsList) {
      if (!(item.myCompletion instanceof ElementFilter)) {
        return true;
      }
    }
    return false;
  }

  protected void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set,
                                         CompletionContext context, CompletionVariantItem item){
    myPeer.addReferenceCompletions(reference, position, set, context, item.myCompletion, item.myTailType);
  }


  private static class Scope{
    Class myClass;
    boolean myIsFinalScope;

    Scope(Class aClass, boolean isFinalScope){
      myClass = aClass;
      myIsFinalScope = isFinalScope;
    }
  }

  protected static class CompletionVariantItem{
    public Object myCompletion;
    public TailType myTailType;

    public CompletionVariantItem(Object completion, TailType tailtype){
      myCompletion = completion;
      myTailType = tailtype;
    }

    public String toString(){
      return myCompletion.toString();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString(){
    return "completion variant at " + myPosition.toString() + " completions: " + myCompletionsList;
  }

  public boolean isCaseInsensitive() {
    return caseInsensitive;
  }

  public void setCaseInsensitive(boolean caseInsensitive) {
    this.caseInsensitive = caseInsensitive;
  }
}
