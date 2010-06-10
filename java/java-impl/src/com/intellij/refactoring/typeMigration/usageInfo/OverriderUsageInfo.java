/*
 * User: anna
 * Date: 27-Mar-2008
 */
package com.intellij.refactoring.typeMigration.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class OverriderUsageInfo extends TypeMigrationUsageInfo{
  private final PsiMethod myBaseMethod;

  public OverriderUsageInfo(@NotNull PsiElement element, PsiMethod baseMethod) {
    super(element);
    myBaseMethod = baseMethod;
  }

  public PsiMethod getBaseMethod() {
    return myBaseMethod;
  }
}