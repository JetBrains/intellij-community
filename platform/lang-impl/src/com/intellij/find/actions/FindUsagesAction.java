// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.find.actions.FindUsagesKt.findUsages;
import static com.intellij.find.actions.ResolverKt.allTargets;
import static com.intellij.find.actions.ResolverKt.findShowUsages;

public class FindUsagesAction extends AnAction implements UpdateInBackground {

  @Experimental
  public static final DataKey<Collection<SearchTarget>> SEARCH_TARGETS = DataKey.create("search.targets");

  public FindUsagesAction() {
    setInjectedContext(true);
  }

  protected boolean toShowDialog() {
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    DataContext dataContext = e.getDataContext();
    findShowUsages(
      project, dataContext, allTargets(dataContext), FindBundle.message("find.usages.ambiguous.title"),
      new UsageVariantHandler() {

        @Override
        public void handleTarget(@NotNull SearchTarget target) {
          SearchScope searchScope = FindUsagesOptions.findScopeByName(
            project, dataContext, FindSettings.getInstance().getDefaultScopeName()
          );
          findUsages(toShowDialog(), project, searchScope, target);
        }

        @Override
        public void handlePsi(@NotNull PsiElement element) {
          startFindUsages(element);
        }
      }
    );
  }

  protected void startFindUsages(@NotNull PsiElement element) {
    FindManager.getInstance(element.getProject()).findUsages(element);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    FindUsagesInFileAction.updateFindUsagesAction(event);
  }

  public static class ShowSettingsAndFindUsages extends FindUsagesAction {
    @Override
    protected void startFindUsages(@NotNull PsiElement element) {
      FindManager.getInstance(element.getProject()).findUsages(element, true);
    }

    @Override
    protected boolean toShowDialog() {
      return true;
    }
  }
}
