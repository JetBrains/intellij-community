// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FieldDeclarationDescriptor implements ItemToReplaceDescriptor {
  private final PsiField myField;
  private final String myName;

  private FieldDeclarationDescriptor(@NotNull PsiField field, @NotNull String name) {
    myField = field;
    myName = name;
  }

  @Nullable
  public static ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiField field) {
    String fieldName = field.getName();
    if (!PsiReflectionAccessUtil.isAccessibleType(field.getType())) {
      return new FieldDeclarationDescriptor(field, fieldName);
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    PsiField newField = elementFactory.createField(myName, PsiReflectionAccessUtil.nearestAccessibleType(myField.getType()));
    myField.replace(newField);
  }
}
