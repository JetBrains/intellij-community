/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.lang.Language;
import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GotoClassAction extends GotoActionBase implements DumbAware {
  public void gotoActionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;
    if (DumbService.getInstance(project).isDumb()) {
      DumbService.getInstance(project).showDumbModeNotification("Goto Class action is not available until indices are built, using Goto File instead");

      myInAction = null;
      new GotoFileAction().actionPerformed(e);
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");
    final GotoClassModel2 model = new GotoClassModel2(project);
    showNavigationPopup(e, model, new GotoActionCallback<Language>() {
      @Override
      protected ChooseByNameFilter<Language> createFilter(@NotNull ChooseByNamePopup popup) {
        return new ChooseByNameLanguageFilter(popup, model, GotoClassSymbolConfiguration.getInstance(project), project);
      }

      @Override
      public void elementChosen(ChooseByNamePopup popup, Object element) {
        AccessToken token = ReadAction.start();
        try {
          if (element instanceof PsiElement) {
            final PsiElement psiElement = getElement(((PsiElement)element), popup);
            NavigationUtil.activateFileWithPsiElement(psiElement, !popup.isOpenInCurrentWindowRequested());
          }
          else {
            EditSourceUtil.navigate(((NavigationItem)element), true, popup.isOpenInCurrentWindowRequested());
          }
        }
        finally {
          token.finish();
        }
      }
    }, "Classes matching pattern");
  }

  private static PsiElement getElement(PsiElement element, ChooseByNamePopup popup) {
    final String path = popup.getPathToAnonymous();    
    if (path != null) {
      
        final String[] classes = path.split("\\$");
        List<Integer> indexes = new ArrayList<Integer>();
        for (String cls : classes) {
          if (cls.isEmpty()) continue;
          try {
            indexes.add(Integer.parseInt(cls) - 1);
          } catch (Exception e) {
            return element;
          }            
        }
        PsiElement current = element;
        for (int index : indexes) {
          final PsiElement[] anonymousClasses = getAnonymousClasses(current);
          if (anonymousClasses.length > index) {
            current = anonymousClasses[index];
          } else {
            return current;
          }
        }
        return current;
    }
    return element;
  }

  static PsiElement[] getAnonymousClasses(PsiElement element) {
    for (AnonymousElementProvider provider : Extensions.getExtensions(AnonymousElementProvider.EP_NAME)) {
      final PsiElement[] elements = provider.getAnonymousElements(element);
      if (elements != null && elements.length > 0) {
        return elements;
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  protected boolean hasContributors(DataContext dataContext) {
    return ChooseByNameRegistry.getInstance().getClassModelContributors().length > 0;
  }
}
