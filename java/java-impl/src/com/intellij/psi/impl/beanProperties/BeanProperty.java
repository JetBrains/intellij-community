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
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provide {@link com.intellij.refactoring.rename.BeanPropertyRenameHandler} if necessary.
 */
@Presentation(icon = "AllIcons.Nodes.Property")
public class BeanProperty {

  private final PsiMethod myMethod;

  protected BeanProperty(@NotNull PsiMethod method) {
    myMethod = method;
  }

  public PsiNamedElement getPsiElement() {
    return new BeanPropertyElement(myMethod, getName()) {
      @Override
      public PsiType getPropertyType() {
        return BeanProperty.this.getPropertyType();
      }
    };
  }

  @NotNull
  public String getName() {
    final String name = PropertyUtilBase.getPropertyName(myMethod);
    return name == null ? "" : name;
  }

  @NotNull
  public PsiType getPropertyType() {
    PsiType type = PropertyUtilBase.getPropertyType(myMethod);
    assert type != null;
    return type;
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  @Nullable
  public PsiMethod getGetter() {
    if (PropertyUtilBase.isSimplePropertyGetter(myMethod)) {
      return myMethod;
    }
    return PropertyUtilBase.findPropertyGetter(myMethod.getContainingClass(), getName(), false, true);
  }

  @Nullable
  public PsiMethod getSetter() {
    if (PropertyUtilBase.isSimplePropertySetter(myMethod)) {
      return myMethod;
    }
    return PropertyUtilBase.findPropertySetter(myMethod.getContainingClass(), getName(), false, true);
  }

  public void setName(String newName) throws IncorrectOperationException {
    final PsiMethod setter = getSetter();
    final PsiMethod getter = getGetter();
    if (getter != null) {
      final String getterName = PropertyUtilBase.suggestGetterName(newName, getter.getReturnType());
      getter.setName(getterName);
    }
    if (setter != null) {
      final String setterName = PropertyUtilBase.suggestSetterName(newName);
      setter.setName(setterName);
    }
  }

  @Nullable
  public Icon getIcon(int flags) {
    return AllIcons.Nodes.Property;
  }

  @Nullable
  public static BeanProperty createBeanProperty(@NotNull PsiMethod method) {
    return PropertyUtilBase.isSimplePropertyAccessor(method) ? new BeanProperty(method) : null;
  }
}
