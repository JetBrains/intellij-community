
package com.intellij.usageView;

import com.intellij.psi.PsiElement;

public interface UsageViewDescriptor {
  String OCCURRENCE_WORD="occurrence";
  String USAGE_WORD="usage";
  String REFERENCE_WORD="reference";
  String INVOCATION_WORD="invocation";

  /**
   * @return an array of elements whose usages were searched or null if not available
   */
  PsiElement[] getElements();

  /**
   * @return usages to be shown
   */
  UsageInfo[] getUsages();

  /**
   * refreshes the list of usages (usage search with the same parameters is performed)
   * @param elements - a non-null array of elements to be refreshed
   */
  void refresh(PsiElement[] elements);

  String getProcessedElementsHeader();

  /**
   * @return true if search is also performed in strings/comments or non-java files
   */
  boolean isSearchInText();

  boolean toMarkInvalidOrReadonlyUsages();

  String getCodeReferencesText(int usagesCount, int filesCount);

  String getCodeReferencesWord();

  String getCommentReferencesText(int usagesCount, int filesCount);

  String getCommentReferencesWord();

  boolean cancelAvailable();

  boolean isCancelInCommonGroup();

  boolean canRefresh();

  boolean willUsageBeChanged(UsageInfo usageInfo);

  String getHelpID();

  boolean canFilterMethods();
}