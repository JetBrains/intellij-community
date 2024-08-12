// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @see RelatedFilesRenamer
 */
public class RelatedFilesRenamerFactory implements AutomaticRenamerFactory {

  @Override
  public boolean isApplicable(final @NotNull PsiElement element) {
    return element instanceof PsiFile &&
           ((PsiFile)element).getVirtualFile() != null &&
           !NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(),
                                                                              ((PsiFile)element).getVirtualFile()).isEmpty();
  }

  @Override
  public @Nullable String getOptionName() {
    return RefactoringBundle.message("rename.related.files.option.name");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(final boolean enabled) {
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(final @NotNull PsiElement element,
                                                 final @NotNull String newName,
                                                 final @NotNull Collection<UsageInfo> usages) {
    return new RelatedFilesRenamer((PsiFile)element, newName);
  }
}
