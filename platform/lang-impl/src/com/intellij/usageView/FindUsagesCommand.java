/**
 * created at Oct 15, 2001
 * @author Jeka
 */
package com.intellij.usageView;

import com.intellij.psi.PsiElement;

public interface FindUsagesCommand {
  /**
   * elements to search should be used when refreshing since
   * the origuinally searched psielement may have become invalid
   */
  UsageInfo[] execute(PsiElement[] elementsToSearch);
}
