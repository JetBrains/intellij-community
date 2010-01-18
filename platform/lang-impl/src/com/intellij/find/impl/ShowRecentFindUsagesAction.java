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

package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ShowRecentFindUsagesAction extends AnAction {
  public void update(final AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(usageView != null && project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<FindUsagesManager.SearchData> history = new ArrayList<FindUsagesManager.SearchData>(findUsagesManager.getFindUsageHistory());

    if (!history.isEmpty()) {
      // skip most recent find usage, it's under your nose
      history.remove(history.size() - 1);
      Collections.reverse(history);
    }
    if (history.isEmpty()) {
      history.add(new FindUsagesManager.SearchData()); // to fill the popup
    }

    BaseListPopupStep<FindUsagesManager.SearchData> step =
      new BaseListPopupStep<FindUsagesManager.SearchData>(FindBundle.message("recent.find.usages.action.title"), history) {
        public Icon getIconFor(final FindUsagesManager.SearchData data) {
          if (data.myElements == null) {
            return null;
          }
          PsiElement psiElement = data.myElements[0].getElement();
          if (psiElement == null) return null;
          return psiElement.getIcon(0);
        }

        @NotNull
        public String getTextFor(final FindUsagesManager.SearchData data) {
          if (data.myElements == null) {
            return FindBundle.message("recent.find.usages.action.nothing");
          }
          PsiElement psiElement = data.myElements[0].getElement();
          if (psiElement == null) return UsageViewBundle.message("node.invalid");
          String scopeString = data.myOptions.searchScope == null ? null : data.myOptions.searchScope.getDisplayName();
          return FindBundle.message("recent.find.usages.action.description",
                                    StringUtil.capitalize(UsageViewUtil.getType(psiElement)),
                                    UsageViewUtil.getDescriptiveName(psiElement),
                                    scopeString == null ? ProjectScope.getAllScope(psiElement.getProject()).getDisplayName() : scopeString);
        }

        public PopupStep onChosen(final FindUsagesManager.SearchData selectedValue, final boolean finalChoice) {
          return doFinalStep(new Runnable() {
            public void run() {
              if (selectedValue.myElements != null) {
                findUsagesManager.rerunAndRecallFromHistory(selectedValue);
              }
            }
          });
        }
      };
    RelativePoint point;
    if (e.getInputEvent() instanceof MouseEvent) {
      point = new RelativePoint((MouseEvent) e.getInputEvent());
    }
    else {
      point = new RelativePoint(usageView.getComponent(), new Point(4, 4));
    }
    JBPopupFactory.getInstance().createListPopup(step).show(point);

  }
}
