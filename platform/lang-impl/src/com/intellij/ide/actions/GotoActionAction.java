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
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import java.awt.*;
import java.util.Map;

public class GotoActionAction extends GotoActionBase implements DumbAware {
  public void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.action");

    showNavigationPopup(e, new GotoActionModel(project, component), new GotoActionCallback<Object>() {
      @Override
      public void elementChosen(ChooseByNamePopup popup, Object element) {
        final AnAction action = (AnAction)((Map.Entry)element).getKey();
        if (action != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final AnActionEvent event = new AnActionEvent(e.getInputEvent(), DataManager.getInstance().getDataContext(component),
                                                            e.getPlace(), (Presentation)action.getTemplatePresentation().clone(), ActionManager.getInstance(),
                                                            e.getModifiers());

              if (ActionUtil.lastUpdateAndCheckDumb(action, event, true)) {
                action.actionPerformed(event);
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    });
  }

}