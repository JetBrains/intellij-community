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

  @Nullable
  Collection<PsiElement> getAdditionalElementsToDelete(PsiElement element, final Collection<PsiElement> allElementsToDelete, final boolean askUser);

  @Nullable
  Collection<String> findConflicts(final PsiElement element, final PsiElement[] allElementsToDelete);

  UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages);

  void prepareForDeletion(PsiElement element) throws IncorrectOperationException;
}
