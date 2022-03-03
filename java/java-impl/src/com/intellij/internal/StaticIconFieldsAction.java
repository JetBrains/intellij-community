// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class StaticIconFieldsAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);


    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabName("Statics");
    presentation.setTabText("Statitcs");
    final UsageView view = UsageViewManager.getInstance(Objects.requireNonNull(project)).showUsages(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation);


    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching icons usages") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final GlobalSearchScope all = GlobalSearchScope.allScope(project);
        PsiClass allIcons = ReadAction.compute(() -> facade.findClass("com.intellij.icons.AllIcons", all));
        if (allIcons != null) {
          searchFields(allIcons, view, indicator);
        }
        PsiClass[] classes = ReadAction.compute(() -> {
          PsiPackage aPackage = facade.findPackage("icons");
          return aPackage != null ? aPackage.getClasses(all) : PsiClass.EMPTY_ARRAY;
        });
        for (PsiClass iconsClass : classes) {
          searchFields(iconsClass, view, indicator);
        }
      }
    });
  }

  private static void searchFields(final PsiClass allIcons, final UsageView view, final ProgressIndicator indicator) {
    ApplicationManager.getApplication().runReadAction(() -> indicator.setText("Searching for: " + allIcons.getQualifiedName()));

    ReferencesSearch.search(allIcons).forEach(reference -> {
      PsiElement elt = reference.getElement();

      while (elt instanceof PsiExpression) elt = elt.getParent();

      if (elt instanceof PsiField) {
        UsageInfo info = new UsageInfo(elt, false);
        view.appendUsage(new UsageInfo2UsageAdapter(info));
      }

      return true;
    });
  }
}

