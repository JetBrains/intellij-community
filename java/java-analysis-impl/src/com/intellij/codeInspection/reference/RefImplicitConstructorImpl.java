/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.reference;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

import java.util.Objects;

public class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {
  RefImplicitConstructorImpl(@NotNull RefClass ownerClass) {
    super(JavaAnalysisBundle.message("inspection.reference.implicit.constructor.name", ownerClass.getName()), ownerClass);
  }

  @Override
  public void buildReferences() {
    getRefManager().fireBuildReferences(this);
  }

  @Override
  public boolean isSuspicious() {
    return ((RefClassImpl)getOwnerClass()).isSuspicious();
  }

  @NotNull
  @Override
  public String getName() {
    return JavaAnalysisBundle.message("inspection.reference.implicit.constructor.name", getOwnerClass().getName());
  }

  @Override
  public String getExternalName() {
    return getOwnerClass().getExternalName();
  }

  @Override
  public boolean isValid() {
    RefClass ownerClass = getOwnerClass();
    return ownerClass != null && ReadAction.compute(ownerClass::isValid).booleanValue();
  }

  @NotNull
  @Override
  public String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  @Override
  public void setAccessModifier(String am) {
    RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
  }

  @Override
  public PsiModifierListOwner getElement() {
    return Objects.requireNonNull(getOwnerClass()).getElement();
  }

  @Override
  public UClass getUastElement() {
    return (UClass)super.getUastElement();
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    RefClass ownerClass = getOwnerClass();
    return ownerClass == null ? null : ownerClass.getPsiElement();
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    return ((RefClassImpl)getOwnerClass()).getContainingFile();
  }

  @Override
  protected void initialize() {
    throw new AssertionError("Should not be called!");
  }
}
