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
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PsiMethodReceiverImpl extends CompositePsiElement implements PsiMethodReceiver {
  public PsiMethodReceiverImpl() {
    super(JavaElementType.METHOD_RECEIVER);
  }

  @Override
  @NotNull
  public PsiElement getDeclarationScope() {
    return getParent();
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getChildrenAsPsiElements(ElementType.ANNOTATIONS, Constants.PSI_ANNOTATION_ARRAY_CONSTRUCTOR);
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }


  @Override
  @NotNull
  public PsiType getType() {
    return JavaPsiFacade.getElementFactory(getProject()).createType(((PsiMethod)getParent()).getContainingClass());
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
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {

  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getName() {
    return "this";
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return false;
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiType getTypeNoResolve() {
    return getType();
  }
}
