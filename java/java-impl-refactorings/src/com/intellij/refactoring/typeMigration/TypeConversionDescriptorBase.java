// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeConversionDescriptorBase {

  private TypeMigrationUsageInfo myRoot;

  public TypeConversionDescriptorBase() {
  }

  public TypeMigrationUsageInfo getRoot() {
    return myRoot;
  }

  public void setRoot(final TypeMigrationUsageInfo root) {
    myRoot = root;
  }

  /**
   * @return converted expression type or null if not known
   */
  public @Nullable PsiType conversionType() {
    return null;
  }

  public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
    return expression;
  }

  @Override
  public String toString() {
    return "$";
  }
}