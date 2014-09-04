/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class StaticIconFieldsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = LangDataKeys.PROJECT.getData(e.getDataContext());


    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabName("Statics");
    presentation.setTabText("Statitcs");
    final UsageView view = UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, new Usage[0], presentation);


    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching icons usages") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final GlobalSearchScope all = GlobalSearchScope.allScope(project);
        PsiClass allIcons = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          public PsiClass compute() {
            return facade.findClass("com.intellij.icons.AllIcons", all);
          }
        });
        searchFields(allIcons, view, indicator);
        PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
          @Override
          public PsiClass[] compute() {
            return facade.findPackage("icons").getClasses(all);
          }
        });
        for (PsiClass iconsClass : classes) {
          searchFields(iconsClass, view, indicator);
        }
      }
    });
  }

  private static void searchFields(final PsiClass allIcons, final UsageView view, final ProgressIndicator indicator) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        indicator.setText("Searching for: " + allIcons.getQualifiedName());
      }
    });

    ReferencesSearch.search(allIcons).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        PsiElement elt = reference.getElement();

        while (elt instanceof PsiExpression) elt = elt.getParent();

        if (elt instanceof PsiField) {
          UsageInfo info = new UsageInfo(elt, false);
          view.appendUsage(new UsageInfo2UsageAdapter(info));
        }

        return true;
      }
    });
  }
}

