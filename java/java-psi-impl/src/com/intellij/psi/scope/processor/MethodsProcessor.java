/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Dec 12, 2002
 * Time: 8:24:29 PM
 * To change this template use Options | File Templates.
 */
public abstract class MethodsProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ElementFilter ourFilter = ElementClassFilter.METHOD;

  private boolean myStaticScopeFlag = false;
  private boolean myIsConstructor = false;
  protected PsiElement myCurrentFileContext = null;
  protected PsiClass myAccessClass = null;
  private PsiExpressionList myArgumentList;
  private PsiType[] myTypeArguments;
  private final LanguageLevel myLanguageLevel;

  public MethodsProcessor(@NotNull PsiConflictResolver[] resolvers,
                          @NotNull List<CandidateInfo> container,
                          @NotNull PsiElement place,
                          @NotNull PsiFile placeFile) {
    super(null, ourFilter, resolvers, container, place, placeFile);
    myLanguageLevel = PsiUtil.getLanguageLevel(placeFile);
  }

  public PsiExpressionList getArgumentList() {
    return myArgumentList;
  }

  public void setArgumentList(@Nullable PsiExpressionList argList) {
    myArgumentList = argList;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void obtainTypeArguments(@NotNull PsiCallExpression callExpression) {
    final PsiType[] typeArguments = callExpression.getTypeArguments();
    if (typeArguments.length > 0) {
      setTypeArguments(typeArguments);
    }
  }

  protected void setTypeArguments(PsiType[] typeParameters) {
    myTypeArguments = typeParameters;
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  public boolean isInStaticScope() {
    return myStaticScopeFlag;
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.START_STATIC) {
      myStaticScopeFlag = true;
    }
    else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public void setAccessClass(PsiClass accessClass) {
      myAccessClass = accessClass;
  }

  public boolean isConstructor() {
    return myIsConstructor;
  }

  public void setIsConstructor(boolean myIsConstructor) {
    this.myIsConstructor = myIsConstructor;
  }

  public void forceAddResult(@NotNull PsiMethod method) {
    add(new CandidateInfo(method, PsiSubstitutor.EMPTY, false, false, myCurrentFileContext));
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }

    return super.getHint(hintKey);
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.METHOD;
  }
}
