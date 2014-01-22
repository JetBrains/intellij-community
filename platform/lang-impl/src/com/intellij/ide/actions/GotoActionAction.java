/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.ide.util.gotoByName.MatchResult;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class GotoActionAction extends GotoActionBase implements DumbAware {
  public static final Comparator<MatchResult> ELEMENTS_COMPARATOR = new Comparator<MatchResult>() {
    @Override
    public int compare(MatchResult o1, MatchResult o2) {
      if (o1.elementName.equals(GotoActionModel.SETTINGS_KEY)) return 1;
      if (o2.elementName.equals(GotoActionModel.SETTINGS_KEY)) return -1;
      return o1.elementName.compareToIgnoreCase(o2.elementName);
    }
  };

  @Override
  public void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.action");
    final GotoActionModel model = new GotoActionModel(project, component, editor, file);
    final GotoActionCallback<Object> callback = new GotoActionCallback<Object>() {
      @Override
      public void elementChosen(ChooseByNamePopup popup, final Object element) {
        final String enteredText = popup.getEnteredText();
        openOptionOrPerformAction(element, enteredText, project, component, e);
      }
    };

    Pair<String, Integer> start = getInitialText(false, e);
    showNavigationPopup(callback, null, createPopup(project, model, start.first, start.second), false);
  }

  private static ChooseByNamePopup createPopup(Project project, GotoActionModel model, String initialText, int initialIndex) {
    return ChooseByNamePopup.createPopup(project,
                                         model,
                                         new DefaultChooseByNameItemProvider(null) {
                                           @Override
                                           protected void sortNamesList(@NotNull String namePattern, @NotNull List<MatchResult> namesList) {
                                             Collections.sort(namesList, ELEMENTS_COMPARATOR);
                                           }
                                         },
                                         initialText,
                                         false,
                                         initialIndex);
  }

  public static void openOptionOrPerformAction(Object element,
                                               final String enteredText,
                                               final Project project,
                                               final Component component,
                                               @Nullable final AnActionEvent e) {
    if (element instanceof OptionDescription) {
      final String configurableId = ((OptionDescription)element).getConfigurableId();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ShowSettingsUtilImpl.showSettingsDialog(project, configurableId, enteredText);
        }
      });
    }
    else {
      //element could be AnAction (SearchEverywhere)
      final AnAction action = element instanceof AnAction ? ((AnAction)element) : (AnAction)((Map.Entry)element).getKey();
      if (action != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (component == null || !component.isShowing()) {
              return;
            }
            final Presentation presentation = action.getTemplatePresentation().clone();
            final DataContext context = DataManager.getInstance().getDataContext(component);
            final AnActionEvent event = new AnActionEvent(e == null ? null : e.getInputEvent(),
                                                          context,
                                                          e == null ? ActionPlaces.UNKNOWN : e.getPlace(),
                                                          presentation,
                                                          ActionManager.getInstance(),
                                                          e == null ? 0 : e.getModifiers());

            if (ActionUtil.lastUpdateAndCheckDumb(action, event, true)) {
              if (action instanceof ActionGroup) {
                JBPopupFactory.getInstance()
                  .createActionGroupPopup(presentation.getText(), (ActionGroup)action, context,
                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
                  .showInBestPositionFor(context);
              } else {
                ActionUtil.performActionDumbAware(action, event);
              }
            }
          }
        }, ModalityState.NON_MODAL);
      }
    }
  }

  @Override
  protected boolean requiresProject() {
    return false;
  }
}