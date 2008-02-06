package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public interface RenamePsiElementProcessor {
  ExtensionPointName<RenamePsiElementProcessor> EP_NAME = ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

  boolean canProcessElement(PsiElement element);
  void renameElement(final PsiElement element, String newName, UsageInfo[] usages,
                     RefactoringElementListener listener) throws IncorrectOperationException;

  @Nullable
  Collection<PsiReference> findReferences(final PsiElement element);

  @Nullable
  Pair<String, String> getTextOccurrenceSearchStrings(final PsiElement element, final String newName);

  @Nullable
  String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava);
}
