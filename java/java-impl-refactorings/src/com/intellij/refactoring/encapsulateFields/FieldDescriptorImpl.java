// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class FieldDescriptorImpl implements FieldDescriptor {
  private PsiField myField;
  private final String myGetterName;
  private final String mySetterName;
  private final PsiMethod myGetterPrototype;
  private final PsiMethod mySetterPrototype;

  public FieldDescriptorImpl(@NotNull PsiField field,
                             @NotNull String getterName,
                             @NotNull String setterName,
                             @Nullable PsiMethod getterPrototype,
                             @Nullable PsiMethod setterPrototype) {
    myField = field;
    myGetterName = getterName;
    mySetterName = setterName;
    myGetterPrototype = getterPrototype;
    mySetterPrototype = setterPrototype;
  }

  @Override
  public @NotNull PsiField getField() {
    return myField;
  }

  @Override
  public @NotNull String getGetterName() {
    return myGetterName;
  }

  @Override
  public @NotNull String getSetterName() {
    return mySetterName;
  }

  @Override
  public @Nullable PsiMethod getGetterPrototype() {
    return myGetterPrototype;
  }

  @Override
  public @Nullable PsiMethod getSetterPrototype() {
    return mySetterPrototype;
  }

  @Override
  public void refreshField(@NotNull PsiField newField) {
    myField = newField;
  }
}
