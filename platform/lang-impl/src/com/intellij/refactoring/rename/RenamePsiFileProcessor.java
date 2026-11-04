// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;


public class RenamePsiFileProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiFileSystemItem;
  }

  @Override
  public @NotNull RenameDialog createRenameDialog(@NotNull Project project, final @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new PsiFileRenameDialog(project, element, nameSuggestionContext, editor);
  }

  private static boolean getSearchForReferences(PsiElement element) {
    return element instanceof PsiFile
      ? RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE
      : RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
  }

  @Override
  public @Unmodifiable @NotNull Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                                        @NotNull SearchScope searchScope,
                                                                        boolean searchInCommentsAndStrings) {
    return findFileReferences(element, searchScope);
  }

  /** @return the references of a file or a directory in {@code searchScope}; none if the settings do not search them */
  @ApiStatus.Internal
  public static @Unmodifiable @NotNull Collection<PsiReference> findFileReferences(@NotNull PsiElement element,
                                                                                  @NotNull SearchScope searchScope) {
    if (!getSearchForReferences(element)) {
      return Collections.emptyList();
    }
    return ReferencesSearch.search(element, searchScope).findAll();
  }

  public static class PsiFileRenameDialog extends RenameWithOptionalReferencesDialog {
    public PsiFileRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
      super(project, element, nameSuggestionContext, editor);
    }

    @Override
    protected boolean getSearchForReferences() {
      return RenamePsiFileProcessor.getSearchForReferences(getPsiElement());
    }

    @Override
    protected void setSearchForReferences(boolean value) {
      if (getPsiElement() instanceof PsiFile) {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE = value;
      }
      else {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
      }
    }
  }

  /**
   * Renames a file or a directory for a caller that has no user.
   * <p>
   * The outer class asks the user nothing, but it does not state the headless rename itself, because
   * it has subclasses in ten products. A subclass must not inherit that statement.
   */
  public static final class HeadlessRenamePsiFileProcessor extends RenamePsiFileProcessor
    implements DelegatingHeadlessRenamePsiElementProcessor {
  }

  /** Renames a file or a directory on the ModPlan engine with this processor, see {@link ModPlanBridgeRenameProvider}. */
  @ApiStatus.Internal
  public static final class ModPlanRenamePsiFileProvider extends ModPlanBridgeRenameProvider {
    public ModPlanRenamePsiFileProvider() {
      super(RenamePsiFileProcessor.class);
    }
  }
}
