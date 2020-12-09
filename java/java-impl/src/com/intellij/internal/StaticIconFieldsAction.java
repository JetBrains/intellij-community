// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
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

public class StaticIconFieldsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(LangDataKeys.PROJECT);


    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabName("Statics");
    presentation.setTabText("Statitcs");
    final UsageView view = UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation);


    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching icons usages") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final GlobalSearchScope all = GlobalSearchScope.allScope(project);
        PsiClass allIcons = ReadAction.compute(() -> facade.findClass("com.intellij.icons.AllIcons", all));
        searchFields(allIcons, view, indicator);
        PsiClass[] classes = ReadAction.compute(() -> facade.findPackage("icons").getClasses(all));
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

