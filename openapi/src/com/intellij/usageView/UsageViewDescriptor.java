/*
* Copyright 2000-2005 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

public interface UsageViewDescriptor {
  String OCCURRENCE_WORD= UsageViewBundle.message("terms.occurrence");
  String USAGE_WORD= UsageViewBundle.message("terms.usage");
  String REFERENCE_WORD= UsageViewBundle.message("terms.reference");
  String INVOCATION_WORD= UsageViewBundle.message("terms.invocation");

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

  @NonNls String getHelpID();

  boolean canFilterMethods();
}