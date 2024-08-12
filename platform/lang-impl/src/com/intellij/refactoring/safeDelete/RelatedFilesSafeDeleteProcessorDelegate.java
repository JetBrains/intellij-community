// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * According to {@link NestingTreeStructureProvider} some files in the Project View are shown as
 * children of another peer file. When going to delete such 'parent' file {@link RelatedFilesSafeDeleteProcessorDelegate}
 * suggests to delete child files as well. Example: when deleting foo.ts file user is suggested to delete generated foo.js and foo.js.map
 * files as well.
 */
public final class RelatedFilesSafeDeleteProcessorDelegate implements SafeDeleteProcessorDelegate {
  @Override
  public boolean handlesElement(final PsiElement element) {
    return element instanceof PsiFile &&
           element.isValid() &&
           ((PsiFile)element).getVirtualFile() != null &&
           !NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(),
                                                                              ((PsiFile)element).getVirtualFile()).isEmpty();
  }

  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(final @NotNull PsiElement element,
                                                              final @NotNull Collection<? extends PsiElement> allElementsToDelete,
                                                              final boolean askUser) {
    if (!askUser || !(element instanceof PsiFile)) return Collections.emptyList();

    final VirtualFile file = ((PsiFile)element).getVirtualFile();
    if (file == null) return Collections.emptyList();

    final Collection<NestingTreeStructureProvider.ChildFileInfo> relatedFileInfos =
      NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(), file);

    final Collection<PsiElement> psiFiles = new ArrayList<>(relatedFileInfos.size());
    for (NestingTreeStructureProvider.ChildFileInfo info : relatedFileInfos) {
      final PsiFile psiFile = element.getManager().findFile(info.file());
      if (psiFile != null && !allElementsToDelete.contains(psiFile)) {
        psiFiles.add(psiFile);
      }
    }

    if (!psiFiles.isEmpty()) {
      final String message = psiFiles.size() == 1
                             ? RefactoringBundle.message("ask.to.delete.related.file", ((PsiFile)psiFiles.iterator().next()).getName())
                             : RefactoringBundle.message("ask.to.delete.related.files",
                                                         StringUtil.join(psiFiles, (psiFile) -> ((PsiFile)psiFile).getName(), ", "));
      final int ok =
        Messages.showYesNoDialog(element.getProject(), message, RefactoringBundle.message("delete.title"), Messages.getQuestionIcon());
      if (ok == Messages.YES) {
        return psiFiles;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
                                           PsiElement @NotNull [] allElementsToDelete,
                                           @NotNull List<? super UsageInfo> result) {
    return null;
  }

  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return Collections.singleton(element);
  }

  @Override
  public Collection<String> findConflicts(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete) {
    return Collections.emptyList();
  }

  @Override
  public UsageInfo[] preprocessUsages(@NotNull Project project, final UsageInfo @NotNull [] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) throws IncorrectOperationException {
  }

  @Override
  public boolean isToSearchInComments(final PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS;
  }

  @Override
  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA;
  }

  @Override
  public void setToSearchInComments(final PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = enabled;
  }

  @Override
  public void setToSearchForTextOccurrences(final PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA = enabled;
  }
}
