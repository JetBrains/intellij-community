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

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SelectInAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.select.in");
    SelectInContext context = SelectInContextImpl.createContext(e);
    if (context == null) return;
    invoke(e.getDataContext(), context);
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SelectInContextImpl.createContext(event) == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  private static void invoke(@NotNull DataContext dataContext, @NotNull SelectInContext context) {
    final List<SelectInTarget> targetVector = Arrays.asList(getSelectInManager(context.getProject()).getTargets());
    ListPopup popup;
    if (targetVector.isEmpty()) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new NoTargetsAction());
      popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("title.popup.select.target"), group, dataContext,
                                                                  JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }
    else {
      popup = JBPopupFactory.getInstance().createListPopup(new SelectInActionsStep(targetVector, context));
    }

    popup.showInBestPositionFor(dataContext);
  }

  private static class SelectInActionsStep extends BaseListPopupStep<SelectInTarget> {
    @NotNull private final SelectInContext mySelectInContext;
    private final List<SelectInTarget> myVisibleTargets;

    public SelectInActionsStep(@NotNull final Collection<SelectInTarget> targetVector, @NotNull SelectInContext selectInContext) {
      mySelectInContext = selectInContext;
      myVisibleTargets = new ArrayList<>();
      for (SelectInTarget target : targetVector) {
        myVisibleTargets.add(target);
      }
      init(IdeBundle.message("title.popup.select.target"), myVisibleTargets, null);
    }

    @Override
    @NotNull
    public String getTextFor(final SelectInTarget value) {
      String text = value.toString();
      String id = value.getMinorViewId() == null ? value.getToolWindowId() : null;
      ToolWindow toolWindow = id == null ? null : ToolWindowManager.getInstance(mySelectInContext.getProject()).getToolWindow(id);
      if (toolWindow != null) {
        text = text.replace(value.getToolWindowId(), toolWindow.getStripeTitle());
      }
      int n = myVisibleTargets.indexOf(value);
      return numberingText(n, text);
    }

    @Override
    public PopupStep onChosen(final SelectInTarget target, final boolean finalChoice) {
      if (finalChoice) {
        target.selectIn(mySelectInContext, true);
        return FINAL_CHOICE;
      }
      if (target instanceof CompositeSelectInTarget) {
        final ArrayList<SelectInTarget> subTargets = new ArrayList<>(((CompositeSelectInTarget)target).getSubTargets(mySelectInContext));
        if (subTargets.size() > 0) {
          Collections.sort(subTargets, new SelectInManager.SelectInTargetComparator());
          return new SelectInActionsStep(subTargets, mySelectInContext);
        }
      }
      return FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(final SelectInTarget selectedValue) {
      return selectedValue instanceof CompositeSelectInTarget &&
             ((CompositeSelectInTarget)selectedValue).getSubTargets(mySelectInContext).size() > 1;
    }

    @Override
    public boolean isSelectable(final SelectInTarget target) {
      if (DumbService.isDumb(mySelectInContext.getProject()) && !DumbService.isDumbAware(target)) {
        return false;
      }
      return target.canSelect(mySelectInContext);
    }

    @Override
    public boolean isMnemonicsNavigationEnabled() {
      return true;
    }
  }

  private static String numberingText(final int n, String text) {
    if (n < 9) {
      text = "&" + (n + 1) + ". " + text;
    }
    else if (n == 9) {
      text = "&" + 0 + ". " + text;
    }
    else {
      text = "&" + (char)('A' + n - 10) + ". " + text;
    }
    return text;
  }

  private static SelectInManager getSelectInManager(Project project) {
    return SelectInManager.getInstance(project);
  }

  private static class NoTargetsAction extends AnAction {
    public NoTargetsAction() {
      super(IdeBundle.message("message.no.targets.available"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }
}