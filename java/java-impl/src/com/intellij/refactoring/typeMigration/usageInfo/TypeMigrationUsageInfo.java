/*
 * User: anna
 * Date: 27-Mar-2008
 */
package com.intellij.refactoring.typeMigration.usageInfo;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class TypeMigrationUsageInfo extends UsageInfo {
  private boolean myExcluded;


  public TypeMigrationUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeMigrationUsageInfo)) return false;

    final UsageInfo usageInfo = (UsageInfo)o;

    if (endOffset != usageInfo.endOffset) return false;
    if (isNonCodeUsage != usageInfo.isNonCodeUsage) return false;
    if (startOffset != usageInfo.startOffset) return false;
    return Comparing.equal(getElement(), usageInfo.getElement());
  }

  public void setExcluded(final boolean excluded) {
    myExcluded = excluded;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

}