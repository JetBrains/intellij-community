/*
 * User: anna
 * Date: 27-Mar-2008
 */
package com.intellij.refactoring.typeMigration.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class TypeMigrationUsageInfo extends UsageInfo {
  private boolean myExcluded;


  public TypeMigrationUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  public void setExcluded(final boolean excluded) {
    myExcluded = excluded;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

}
