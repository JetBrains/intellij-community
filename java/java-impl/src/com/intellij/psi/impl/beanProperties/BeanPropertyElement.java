/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.beanProperties;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public class BeanPropertyElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiMethod myMethod;
  private final String myName;

  public BeanPropertyElement(@NotNull PsiMethod method, @NotNull String name) {
    myMethod = method;
    myName = name;
  }

  @Nullable
  public PsiType getPropertyType() {
    return PropertyUtilBase.getPropertyType(myMethod);
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myMethod;
  }

  @Override
  public PsiManager getManager() {
    return myMethod.getManager();
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {

  }

  @NotNull
  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public Icon getIcon(boolean flags) {
    return AllIcons.Nodes.Property;
  }

  @Override
  public PsiElement getParent() {
    return myMethod;
  }

  @Override
  @Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public String getTypeName() {
    return IdeBundle.message("bean.property");
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return getIcon(0);
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.from(0, 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeanPropertyElement element = (BeanPropertyElement)o;

    if (!myMethod.equals(element.myMethod)) return false;
    if (!myName.equals(element.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethod.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}
