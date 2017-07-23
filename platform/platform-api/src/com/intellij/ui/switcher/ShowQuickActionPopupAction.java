/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING;

public class ShowQuickActionPopupAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    QuickActionProvider quickActionProvider = e.getData(QuickActionProvider.KEY);
    if (quickActionProvider == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    List<AnAction> actions = quickActionProvider.getActions(true);
    e.getPresentation().setEnabled(!actions.isEmpty());
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    QuickActionProvider provider = e.getRequiredData(QuickActionProvider.KEY);
    List<AnAction> actions = provider.getActions(true);

    DefaultActionGroup group = new DefaultActionGroup(actions);
    group.addSeparator();

    JComponent component = provider.getComponent();
    if (component != null && !provider.isCycleRoot()) {
      Component eachParent = component.getParent();
      while (eachParent != null) {
        QuickActionProvider parentProvider = ObjectUtils.tryCast(eachParent, QuickActionProvider.class);
        if (parentProvider != null) {
          List<AnAction> parentActions = parentProvider.getActions(false);
          if (!parentActions.isEmpty()) {
            String name = StringUtil.notNullize(parentProvider.getName(), "");
            DefaultActionGroup parentGroup = new DefaultActionGroup(name, parentActions);
            if (!StringUtil.isEmpty(name)) {
              parentGroup.setPopup(true);
            }
            else {
              group.add(Separator.getInstance());
            }
            group.add(parentGroup);
          }
          if (parentProvider.isCycleRoot()) break;
        }
        eachParent = eachParent.getParent();
      }
    }

    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, e.getDataContext(), ALPHA_NUMBERING, true)
      .showInBestPositionFor(e.getDataContext());
  }
}
