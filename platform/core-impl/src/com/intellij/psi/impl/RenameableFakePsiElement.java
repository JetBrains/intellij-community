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
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public abstract class RenameableFakePsiElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiElement myParent;

  protected RenameableFakePsiElement(final PsiElement parent) {
    myParent = parent;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiFile getContainingFile() {
    return myParent.getContainingFile();
  }

  @Override
  public abstract String getName();

  @Override
  @NotNull
  public Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myParent.getProject();
  }

  @Override
  public PsiManager getManager() {
    return PsiManager.getInstance(getProject());
  }

  @Override
  @Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  @NonNls
  public String getName(final PsiElement context) {
    return getName();
  }

  @Override
  public void init(final PsiElement element) {
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public final Icon getIcon(final boolean open) {
    return getIcon();
  }

  @Override
  @Nullable
  public TextRange getTextRange() {
    return TextRange.from(0, 0);
  }
}
