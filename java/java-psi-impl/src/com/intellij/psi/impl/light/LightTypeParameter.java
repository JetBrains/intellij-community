/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightTypeParameter extends LightClass implements PsiTypeParameter {
  public LightTypeParameter(final PsiTypeParameter delegate) {
    super(delegate);
  }

  @NotNull
  @Override
  public PsiTypeParameter getDelegate() {
    return (PsiTypeParameter)super.getDelegate();
  }

  @NotNull
  @Override
  public PsiElement copy() {
    return new LightTypeParameter(getDelegate());
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    return getDelegate().getOwner();
  }

  @Override
  public int getIndex() {
    return getDelegate().getIndex();
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getDelegate().getAnnotations();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getDelegate().getApplicableAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls final String qualifiedName) {
    return getDelegate().findAnnotation(qualifiedName);
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls final String qualifiedName) {
    return getDelegate().addAnnotation(qualifiedName);
  }

  public boolean useDelegateToSubstitute() {
    return true;
  }

  @Override
  public String toString() {
    return "PsiTypeParameter:" + getName();
  }
}
