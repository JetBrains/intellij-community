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

package com.intellij.facet.impl.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopupStep;

import javax.swing.*;

/**
 * @author nik
 */
public class GroupToolbarAction extends AnAction {
  private final ActionGroup myGroup;
  private final JComponent myToolbarComponent;

  public GroupToolbarAction(final ActionGroup group, JComponent toolbarComponent) {
    super(group.getTemplatePresentation().getText(), group.getTemplatePresentation().getDescription(),
          group.getTemplatePresentation().getIcon());
    myGroup = group;
    myToolbarComponent = toolbarComponent;
  }

  public void actionPerformed(AnActionEvent e) {
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final ListPopupStep popupStep = popupFactory.createActionsStep(myGroup, e.getDataContext(), false, false,
                                                                   myGroup.getTemplatePresentation().getText(), myToolbarComponent, false,
                                                                   0, false);
    popupFactory.createListPopup(popupStep).showUnderneathOf(myToolbarComponent);
  }

  public void update(AnActionEvent e) {
    myGroup.update(e);
  }
}
