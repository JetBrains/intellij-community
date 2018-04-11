// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JvmEnumConstantClassValue implements JvmClassValue {

  private final JvmEnumConstantValue myEnumValue;
  private final JvmClass myClass;

  JvmEnumConstantClassValue(@NotNull JvmEnumConstantValue enumValue, @NotNull JvmClass clazz) {
    myEnumValue = enumValue;
    myClass = clazz;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myClass.getQualifiedName();
  }

  @Override
  public JvmClass getClazz() {
    return myClass;
  }

  // delegate other methods to enum value

  @Nullable
  @Override
  public PsiElement getSourceElement() {
    return myEnumValue.getSourceElement();
  }

  @Override
  public boolean isValid() {
    return myEnumValue.isValid();
  }

  @Override
  public void navigate(boolean requestFocus) {
    myEnumValue.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myEnumValue.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myEnumValue.canNavigateToSource();
  }
}
