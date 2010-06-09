/*
 * User: anna
 * Date: 27-Mar-2008
 */
package com.intellij.refactoring.typeMigration.usageInfo;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class OverridenUsageInfo extends TypeMigrationUsageInfo {
  private OverriderUsageInfo[] myOverriders;

  public OverridenUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  public OverriderUsageInfo[] getOverridingElements() {
    return myOverriders;
  }

  public void setOverriders(final OverriderUsageInfo[] overriders) {
    myOverriders = overriders;
  }
}