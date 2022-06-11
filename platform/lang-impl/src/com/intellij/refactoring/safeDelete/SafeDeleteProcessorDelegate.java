// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * An extension point for "Safe Delete" refactoring.
 * 
 * Delegates are processed one by one, the first delegate which agrees to handle element ({@link #handlesElement(PsiElement)}) will be used, rest are ignored.
 * Natural loading order can be changed by providing attribute "order" during registration in plugin.xml.
 */
public interface SafeDeleteProcessorDelegate {
  ExtensionPointName<SafeDeleteProcessorDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.safeDeleteProcessor");

  /**
   * @return {@code true} if delegates can process {@code element}.
   */
  boolean handlesElement(PsiElement element);

  /**
   * Find usages of the {@code element} and fill {@code result} with them. 
   * Is called during {@link BaseRefactoringProcessor#findUsages()} under modal progress in read action.
   * 
   * @param element              an element selected for deletion.
   * @param allElementsToDelete  all elements selected for deletion.
   * @param result               list of {@link UsageInfo} to store found usages
   * @return                     {@code null} if element should not be searched in text occurrences/comments though corresponding settings were enabled, otherwise
   *                             bean with the information how to detect if an element is inside all elements to delete (e.g. {@link SafeDeleteProcessor#getDefaultInsideDeletedCondition(PsiElement[])})
   *                             and current element.
   */
  @Nullable
  NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete, @NotNull List<UsageInfo> result);

  /**
   * Returns elements that are searched for usages of the element selected for deletion. Called before the refactoring dialog is shown.
   * May show UI to ask if additional elements should be deleted along with the specified selected element.
   *
   * @param element an element selected for deletion.
   * @param allElementsToDelete all elements selected for deletion.
   * @return additional elements to search for usages, or null if the user has cancelled the refactoring.
   */
  @Nullable
  Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element, @NotNull Collection<PsiElement> allElementsToDelete);

  /**
   * Returns the list of additional elements to be deleted. Called after the refactoring dialog is shown.
   * May show UI to ask the user if some additional elements should be deleted along with the
   * specified selected element.
   *
   * @param element an element selected for deletion.
   * @param allElementsToDelete all elements selected for deletion.
   * @return additional elements to search for usages, or null if no additional elements were chosen.
   */
  @Nullable
  Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element, @NotNull Collection<PsiElement> allElementsToDelete, final boolean askUser);

  /**
   * Detects usages which are not safe to delete.
   *
   * @param  element an element selected for deletion.
   * @param  allElementsToDelete all elements selected for deletion.
   * @return collection of conflict messages which would be shown to the user before delete can be performed.
   */
  @Nullable
  Collection<@Nls String> findConflicts(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete);

  /**
   * Called after the user has confirmed the refactoring. Can filter out some of the usages
   * found by the refactoring. May show UI to ask the user if some of the usages should
   * be excluded.
   *
   * @param project the project where the refactoring happens.
   * @param usages all usages to be processed by the refactoring. 
   * @return the filtered list of usages, or null if the user has cancelled the refactoring.
   */
  UsageInfo @Nullable [] preprocessUsages(Project project, UsageInfo[] usages);

  /**
   * Prepares an element for deletion e.g., normalizing declaration so the element declared in the same declaration won't be affected by deletion.
   * 
   * Called during {@link BaseRefactoringProcessor#performRefactoring(UsageInfo[])} under write action
   * 
   * @param  element an element selected for deletion.
   * @throws IncorrectOperationException
   */
  void prepareForDeletion(PsiElement element) throws IncorrectOperationException;

  /**
   * Called to set initial value for "Search in comments" checkbox.
   * @return {@code true} if previous safe delete was executed with "Search in comments" option on.
   */
  boolean isToSearchInComments(final PsiElement element);

  /**
   * Called to save chosen for given {@code element} "Search in comments" value.
   */
  void setToSearchInComments(final PsiElement element, boolean enabled);

    /**
   * Called to set initial value for "Search for text occurrence" checkbox.
   * @return {@code true} if previous safe delete was executed with "Search for test occurrences" option on.
   */
  boolean isToSearchForTextOccurrences(final PsiElement element);

   /**
   * Called to save chosen for given {@code element} "Search for text occurrences" value.
   */
  void setToSearchForTextOccurrences(final PsiElement element, boolean enabled);
}
