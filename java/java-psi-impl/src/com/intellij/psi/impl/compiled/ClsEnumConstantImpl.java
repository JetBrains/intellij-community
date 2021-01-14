// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author ven
 */
public class ClsEnumConstantImpl extends ClsFieldImpl implements PsiEnumConstant {
  public ClsEnumConstantImpl(@NotNull PsiFieldStub stub) {
    super(stub);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);
    appendText(getModifierList(), indentLevel, buffer, "");
    appendText(getNameIdentifier(), indentLevel, buffer);
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiField mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getDocComment(), mirror.getDocComment());
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
  }

  @Override
  public PsiExpressionList getArgumentList() {
    return null;
  }

  @Override
  public PsiMethod resolveMethod() {
    return null;
  }

  @Override
  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  public PsiEnumConstantInitializer getInitializingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
    throw new IncorrectOperationException("cannot create initializing class in cls enum constant");
  }

  @Override
  public PsiMethod resolveConstructor() {
    return null;
  }

  @Override
  @NotNull
  public PsiType getType() {
    return JavaPsiFacade.getElementFactory(getProject()).createType(getContainingClass());
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return true;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    return this;
  }
}
