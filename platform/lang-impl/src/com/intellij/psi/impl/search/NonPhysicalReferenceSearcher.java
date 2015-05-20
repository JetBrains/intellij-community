package com.intellij.psi.impl.search;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 * This searcher does the job for various console and fragment editors and other non-physical files.
 * We need this because ScopeEnlarger functionality will not work for nonphysical files.
 */
public class NonPhysicalReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public NonPhysicalReferenceSearcher() {
    super(true);
  }

  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    final SearchScope scope = queryParameters.getScopeDeterminedByUser();
    final PsiElement element = queryParameters.getElementToSearch();
    final PsiFile containingFile = element.getContainingFile();
    if (isPhysicalAndNonScratch(containingFile) && !(scope instanceof GlobalSearchScope)) return;
    final LocalSearchScope currentScope;
    if (scope instanceof LocalSearchScope) {
      if (queryParameters.isIgnoreAccessScope()) return;
      currentScope = (LocalSearchScope)scope;
    }
    else {
      currentScope = null;
    }
    Project project = element.getProject();
    if (!project.isInitialized()) return; // skip default and other projects that look funny
    PsiManager psiManager = PsiManager.getInstance(project);

    for (VirtualFile virtualFile : FileEditorManager.getInstance(project).getOpenFiles()) {
      if (virtualFile.getFileType().isBinary()) continue;
      PsiFile file = psiManager.findFile(virtualFile);

      if (isPhysicalAndNonScratch(file)) continue;
      LocalSearchScope newScope = new LocalSearchScope(file);
      LocalSearchScope searchScope = currentScope == null ? newScope : newScope.intersectWith(currentScope);
      ReferencesSearch.searchOptimized(element, searchScope, true, queryParameters.getOptimizer(), consumer);
    }
  }

  private static boolean isPhysicalAndNonScratch(PsiFile file) {
    if (file == null || file instanceof PsiCodeFragment) return true;
    return file.getViewProvider().isPhysical() &&
           ScratchFileService.getInstance().getRootType(file.getVirtualFile()) == null;
  }
}
