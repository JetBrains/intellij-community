// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameRefactoringDialog;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.openapi.util.NlsContexts.*;

@ApiStatus.Internal
public final class RefactoringUiServiceImpl extends RefactoringUiService {
  @Override
  public RenameRefactoringDialog createRenameRefactoringDialog(Project project,
                                                               PsiElement element,
                                                               PsiElement context,
                                                               Editor editor) {
    return new RenameDialog(project, element, context, editor);
  }

  @Override
  public int showReplacePromptDialog(boolean isMultipleFiles, @DialogTitle String title, Project project) {
    ReplacePromptDialog promptDialog = new ReplacePromptDialog(isMultipleFiles, title, project);
    promptDialog.show();
    return promptDialog.getExitCode();
  }

  @Override
  public void setStatusBarInfo(@NotNull Project project, @NotNull @StatusBarText String message) {
    StatusBarUtil.setStatusBarInfo(project, message);
  }

  @Override
  public ConflictsDialogBase createConflictsDialog(@NotNull Project project,
                                                   @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                   @Nullable Runnable doRefactoringRunnable,
                                                   boolean alwaysShowOkButton, boolean canShowConflictsInView) {
    return new BaseRefactoringProcessorUi().createConflictsDialog(project, conflicts, doRefactoringRunnable, alwaysShowOkButton, canShowConflictsInView);
  }

  @Override
  public void startFindUsages(PsiElement element, FindUsagesOptions options) {
    ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager().startFindUsages(element, options);
  }

  @Override
  public void highlightUsageReferences(PsiElement file,
                                       PsiElement target,
                                       @NotNull Editor editor, boolean clearHighlights) {

    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }

    Project project = target.getProject();
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    final FindUsagesHandlerBase handler = findUsagesManager.getFindUsagesHandler(target, true);

    // in case of injected file, use host file to highlight all occurrences of the target in each injected file
    PsiFile context = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    SearchScope searchScope = new LocalSearchScope(context);
    Collection<PsiReference> refs = handler == null
                                    ? ReferencesSearch.search(target, searchScope, false).findAll()
                                    : handler.findReferencesToHighlight(target, searchScope);

    new HighlightUsagesHandler.DoHighlightRunnable(new ArrayList<>(refs), project, target,
                                                   editor, context, clearHighlights).run();
  }

  @Override
  public void findUsages(@NotNull Project project, @NotNull PsiElement psiElement, @Nullable PsiFile scopeFile, FileEditor editor, boolean showDialog, @Nullable("null means default (stored in options)") SearchScope searchScope) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    findUsagesManager.findUsages(psiElement, scopeFile, editor, showDialog, searchScope);
  }

  @Override
  public boolean showRefactoringMessageDialog(@DialogTitle String title, @DialogMessage String message,
                                              @NonNls String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    final RefactoringMessageDialog dialog =
      new RefactoringMessageDialog(title, message, helpTopic, iconId, showCancelButton, project);
    return dialog.showAndGet();
  }
}