// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public abstract class MethodsProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ElementFilter ourFilter = ElementClassFilter.METHOD;

  private boolean myStaticScopeFlag;
  private boolean myIsConstructor;
  protected PsiElement myCurrentFileContext;
  PsiClass myAccessClass;
  private PsiExpressionList myArgumentList;
  private PsiType[] myTypeArguments;
  private final LanguageLevel myLanguageLevel;

  MethodsProcessor(PsiConflictResolver @NotNull [] resolvers,
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

  private void setTypeArguments(PsiType[] typeParameters) {
    myTypeArguments = typeParameters;
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  boolean isInStaticScope() {
    return myStaticScopeFlag;
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    if (JavaScopeProcessorEvent.isEnteringStaticScope(event, associated)) {
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

  public PsiElement getCurrentFileContext() {
    return myCurrentFileContext;
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
  public boolean shouldProcess(@NotNull DeclarationKind kind) {
    return kind == DeclarationKind.METHOD;
  }
}
