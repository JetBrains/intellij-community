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
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;

public class GotoClassAction extends GotoActionBase implements DumbAware {
  public void gotoActionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;
    if (DumbService.getInstance(project).isDumb()) {
      DumbService.getInstance(project).showDumbModeNotification("Goto Class action is not available until indices are built, using Goto File instead");

      myInAction = null;
      new GotoFileAction().gotoActionPerformed(e);
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoClassModel2(project), getPsiContext(e));

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (GotoClassAction.class.equals(myInAction)) {
          myInAction = null;
        }
      }

      public void elementChosen(Object element) {
        if (element instanceof PsiElement) {
          NavigationUtil.activateFileWithPsiElement((PsiElement)element);
        }
        else {
          ((NavigationItem)element).navigate(true);
        }
      }
    }, ModalityState.current(), true);
  }

  protected boolean hasContributors(DataContext dataContext) {
    return ChooseByNameRegistry.getInstance().getClassModelContributors().length > 0;
  }
}
