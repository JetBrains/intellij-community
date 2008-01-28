package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public interface SafeDeleteProcessorDelegate {
  ExtensionPointName<SafeDeleteProcessorDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.safeDeleteProcessor");

  boolean handlesElement(PsiElement element);
  @Nullable
  NonCodeUsageSearchInfo findUsages(final PsiElement element, final PsiElement[] allElementsToDelete, List<UsageInfo> result);

  /**
   * Called before the refactoring dialog is shown. Returns the list of elements for which the
   * usages should be searched for the specified element selected by the user for deletion.
   * May show UI to ask the user if some additional elements should be deleted along with the
   * specified selected element.
   *
   * @param element an element selected for deletion.
   * @param allElementsToDelete
   * @return additional elements to search for usages, or null if the user has cancelled the refactoring.
   */
  @Nullable
  Collection<? extends PsiElement> getElementsToSearch(final PsiElement element, final Collection<PsiElement> allElementsToDelete);

  @Nullable
  Collection<PsiElement> getAdditionalElementsToDelete(PsiElement element, final Collection<PsiElement> allElementsToDelete, final boolean askUser);

  @Nullable
  Collection<String> findConflicts(final PsiElement element, final PsiElement[] allElementsToDelete);

  UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages);

  void prepareForDeletion(PsiElement element) throws IncorrectOperationException;
}
