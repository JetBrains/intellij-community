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
import java.util.Map;

/**
 * @author yole
 */
public abstract class RenamePsiElementProcessor {
  public static final ExtensionPointName<RenamePsiElementProcessor> EP_NAME = ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

  public abstract boolean canProcessElement(PsiElement element);
  public abstract void renameElement(final PsiElement element, String newName, UsageInfo[] usages,
                     RefactoringElementListener listener) throws IncorrectOperationException;

  @Nullable
  public Collection<PsiReference> findReferences(final PsiElement element) {
    return null;
  }

  @Nullable
  public Pair<String, String> getTextOccurrenceSearchStrings(final PsiElement element, final String newName) {
    return null;
  }

  @Nullable
  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return null;
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
  }
}
