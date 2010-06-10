package com.intellij.refactoring.typeMigration;

import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;

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

  public void replace(PsiExpression expression){}

  @Override
  public String toString() {
    return "$";
  }
}