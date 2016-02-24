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

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionRVContentProvider;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class InvokeQuickFixAction extends AnAction {
  private final InspectionResultsView myView;

  public InvokeQuickFixAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.apply.quickfix"), InspectionsBundle.message("inspection.action.apply.quickfix.description"),
          AllIcons.Actions.CreateFromUsage);
    myView = view;
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
                              myView.getTree());
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    InspectionToolWrapper toolWrapper = myView.getTree().getSelectedToolWrapper();
    final InspectionRVContentProvider provider = myView.getProvider();
    if (toolWrapper != null && provider.isContentLoaded()) {
      presentation.setEnabled(provider.hasQuickFixes(myView.getTree()));
    }
    else {
      presentation.setEnabled(false);
    }
  }

  private static ActionGroup getFixes(final QuickFixAction[] quickFixes) {
    return new ActionGroup() {
      @Override
      @NotNull
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        List<QuickFixAction> children = new ArrayList<QuickFixAction>();
        for (QuickFixAction fix : quickFixes) {
          if (fix != null) {
            children.add(fix);
          }
        }
        return children.toArray(new AnAction[children.size()]);
      }
    };
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    InspectionToolWrapper toolWrapper = myView.getTree().getSelectedToolWrapper();
    assert toolWrapper != null;
    final QuickFixAction[] quickFixes = myView.getProvider().getQuickFixes(toolWrapper, myView.getTree());
    if (quickFixes == null || quickFixes.length == 0) {
      Messages.showInfoMessage(myView, "There are no applicable quickfixes", "Nothing found to fix");
      return;
    }
    ActionGroup fixes = getFixes(quickFixes);
    DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(InspectionsBundle.message("inspection.tree.popup.title"),
                              fixes,
                              dataContext,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    InspectionResultsView.showPopup(e, popup);
  }
}
