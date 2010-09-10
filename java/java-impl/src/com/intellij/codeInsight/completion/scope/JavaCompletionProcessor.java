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
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class JavaCompletionProcessor extends BaseScopeProcessor implements ElementClassHint {
  public static final Key<Condition<String>> NAME_FILTER = Key.create("NAME_FILTER");
  public static final Key<Boolean> JAVA_COMPLETION = Key.create("JAVA_COMPLETION");

  private boolean myStatic = false;
  private final Set<Object> myResultNames = new THashSet<Object>();
  private final List<CompletionElement> myResults;
  private final PsiElement myElement;
  private final PsiElement myScope;
  private CodeInsightSettings mySettings = null;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;
  private final Condition<String> myMatcher;
  private final boolean myCheckAccess;

  public JavaCompletionProcessor(PsiElement element, ElementFilter filter, final boolean checkAccess, @Nullable Condition<String> nameCondition) {
    myCheckAccess = checkAccess;
    mySettings = CodeInsightSettings.getInstance();
    myResults = new ArrayList<CompletionElement>();
    myElement = element;
    myMatcher = nameCondition;
    myFilter = filter;
    PsiElement scope = element;
    if (JavaResolveUtil.isInJavaDoc(myElement)) myMembersFlag = true;
    while(scope != null && !(scope instanceof PsiFile) && !(scope instanceof PsiClass)){
      scope = scope.getContext();
    }
    myScope = scope;
    if (!(element.getContainingFile() instanceof PsiJavaFile)) {
      myMembersFlag = true;
    }

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        myQualifierClass = JavaResolveUtil.getContextClass(myElement);
        if (myQualifierClass != null) {
          myQualifierType = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory().createType(myQualifierClass);
        }
      }
      else if (qualifier != null) {
        myQualifierType = qualifier.getType();
        myQualifierClass = PsiUtil.resolveClassInType(myQualifierType);
        if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (target instanceof PsiClass) {
            myQualifierClass = (PsiClass)target;
          }
        }
      }
    }
  }

  public void handleEvent(Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC){
      myStatic = true;
    }
    if(event == JavaScopeProcessorEvent.CHANGE_LEVEL){
      myMembersFlag = true;
    }
  }

  public boolean execute(PsiElement element, ResolveState state){
    if(!(element instanceof PsiClass) && element instanceof PsiModifierListOwner){
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if(myStatic){
        if(!modifierListOwner.hasModifierProperty(PsiModifier.STATIC)){
          // we don't need non static method in static context.
          return true;
        }
      }
      else{
        if(!mySettings.SHOW_STATIC_AFTER_INSTANCE
           && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
           && !myMembersFlag){
          // according settings we don't need to process such fields/methods
          return true;
        }
      }
    }
    final PsiElement elementParent = myElement.getParent();
    if (element instanceof PsiPackage && myScope instanceof PsiClass) {
      if (!(elementParent instanceof PsiQualifiedReference && ((PsiQualifiedReference)elementParent).getQualifier() != null)) {
        return true;
      }
    }

    if (myFilter.isClassAcceptable(element.getClass())
        && myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement)) {
      final String name = PsiUtil.getName(element);
      if (StringUtil.isNotEmpty(name) && (myMatcher == null || myMatcher.value(name))) {
        if(isAccessible(element)){
          add(new CompletionElement(myQualifierType, element, state.get(PsiSubstitutor.KEY), myQualifierClass));
        }
      }
    }
    return true;
  }

  @Nullable
  public PsiType getQualifierType() {
    return myQualifierType;
  }

  private boolean isAccessible(final PsiElement element) {
    if (!myCheckAccess) return true;
    if (!(element instanceof PsiMember)) return true;

    return JavaPsiFacade.getInstance(element.getProject()).getResolveHelper().isAccessible((PsiMember)element, myElement, myQualifierClass);
  }

  private void add(CompletionElement element){
    if(myResultNames.add(element.getUniqueId())){
      myResults.add(element);
    }
  }

  public void setCompletionElements(@NotNull Object[] elements) {
    for (Object element: elements) {
      myResults.add(new CompletionElement(null, element, PsiSubstitutor.EMPTY, myQualifierClass));
    }
  }

  public Set<CompletionElement> getResults(){
    return new THashSet<CompletionElement>(myResults);
  }

  public void clear() {
    myResults.clear();
  }

  public boolean shouldProcess(DeclaractionKind kind) {
    switch (kind) {
      case CLASS:
        return myFilter.isClassAcceptable(PsiClass.class);

      case FIELD:
        return myFilter.isClassAcceptable(PsiField.class);

      case METHOD:
        return myFilter.isClassAcceptable(PsiMethod.class);

      case PACKAGE:
        return myFilter.isClassAcceptable(PsiPackage.class);

      case VARIABLE:
        return myFilter.isClassAcceptable(PsiVariable.class);

      case ENUM_CONST:
        return myFilter.isClassAcceptable(PsiEnumConstant.class);
    }

    return false;
  }

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    if (hintKey == NAME_FILTER) {
      return (T)myMatcher;
    }
    if (hintKey == JAVA_COMPLETION) {
      return (T)Boolean.TRUE;
    }

    return super.getHint(hintKey);
  }
}
