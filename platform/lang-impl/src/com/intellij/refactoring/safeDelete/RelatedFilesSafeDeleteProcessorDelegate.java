// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class RelatedFilesSafeDeleteProcessorDelegate implements SafeDeleteProcessorDelegate {
  /**
   * Opts the related files of a file in to the headless safe delete. The legacy question "delete the related files too?" is answered by
   * its default: no.
   */
  @Override
  public @NotNull ModPlanSafeDeleteProvider asModPlan() {
    return new ModPlanLegacySafeDeleteProvider(this);
  }

  @Override
  public boolean handlesElement(final PsiElement element) {
    return element instanceof PsiFile file &&
           element.isValid() &&
           file.getVirtualFile() != null &&
           !NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(), file.getVirtualFile()).isEmpty();
  }

  @Override
  public @NotNull AdditionalElementsData getAdditionalElementsToDelete(final @NotNull PsiElement element,
                                                                       final @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    if (!(element instanceof PsiFile f)) return AdditionalElementsData.NONE;

    final VirtualFile file = f.getVirtualFile();
    if (file == null) return AdditionalElementsData.NONE;

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
      return new AdditionalElementsData(psiFiles, new ConfirmationPolicy.Ask(message, false));
    }

    return AdditionalElementsData.NONE;
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
