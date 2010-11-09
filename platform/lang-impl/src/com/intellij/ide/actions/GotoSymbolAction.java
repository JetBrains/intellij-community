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

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public class GotoSymbolAction extends GotoActionBase {

  public void gotoActionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.symbol");
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final GotoSymbolModel2 model = new GotoSymbolModel2(project);
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, getPsiContext(e),
                                                                  getInitialText(e.getData(PlatformDataKeys.EDITOR)));
    final ChooseByNameFilter filterUI = new ChooseByNameLanguageFilter(popup, model, GotoClassSymbolConfiguration.getInstance(project),
                                                                       project);
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (GotoSymbolAction.class.equals(myInAction)) {
          myInAction = null;
        }
        filterUI.close();
      }

      public void elementChosen(Object element) {
        ((NavigationItem)element).navigate(true);
      }
    }, ModalityState.current(), true);
  }

  protected boolean hasContributors(DataContext dataContext) {
    return ChooseByNameRegistry.getInstance().getSymbolModelContributors().length > 0;
  }
}