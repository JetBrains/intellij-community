// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.beanProperties;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
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
  private final boolean myAcceptBoxedBooleanIsPrefix;

  protected BeanProperty(@NotNull PsiMethod method) {
    this(method, false);
  }

  private BeanProperty(@NotNull PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    myMethod = method;
    myAcceptBoxedBooleanIsPrefix = acceptBoxedBooleanIsPrefix;
  }

  public PsiNamedElement getPsiElement() {
    return new BeanPropertyElement(myMethod, getName()) {
      @Override
      public PsiType getPropertyType() {
        return BeanProperty.this.getPropertyType();
      }
    };
  }

  public @NotNull String getName() {
    final String name = PropertyUtilBase.getPropertyName(myMethod, myAcceptBoxedBooleanIsPrefix);
    return name == null ? "" : name;
  }

  public @NotNull PsiType getPropertyType() {
    PsiType type;
    if (PropertyUtilBase.isSimplePropertyGetter(myMethod, myAcceptBoxedBooleanIsPrefix)) {
      type = myMethod.getReturnType();
    }
    else if (PropertyUtilBase.isSimplePropertySetter(myMethod)) {
      type = myMethod.getParameterList().getParameters()[0].getType();
    }
    else {
      type = null;
    }
    assert type != null;
    return type;
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public @Nullable PsiMethod getGetter() {
    if (PropertyUtilBase.isSimplePropertyGetter(myMethod, myAcceptBoxedBooleanIsPrefix)) {
      return myMethod;
    }
    return PropertyUtilBase.findPropertyGetter(myMethod.getContainingClass(), getName(), false, true, myAcceptBoxedBooleanIsPrefix);
  }

  public @Nullable PsiMethod getSetter() {
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

  public @NotNull Icon getIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
  }

  public static @Nullable BeanProperty createBeanProperty(@NotNull PsiMethod method) {
    return createBeanProperty(method, false);
  }

  public static @Nullable BeanProperty createBeanProperty(@NotNull PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    return PropertyUtilBase.isSimplePropertyAccessor(method, acceptBoxedBooleanIsPrefix) ?
           new BeanProperty(method, acceptBoxedBooleanIsPrefix) : null;
  }
}
