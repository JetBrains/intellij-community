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
package com.intellij.dvcs.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class BranchActionGroupPopup extends PopupFactoryImpl.ActionGroupPopup {
  public BranchActionGroupPopup(@NotNull String title, @NotNull Project project,
                                @NotNull Condition<AnAction> preselectActionCondition, @NotNull ActionGroup actions) {
    super(title, actions, SimpleDataContext.getProjectContext(project), false, false, true, false, null, -1,
          preselectActionCondition, null);
  }

  @Override
  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    WizardPopup popup = super.createPopup(parent, step, parentValue);
    RootAction rootAction = getRootAction(parentValue);
    if (rootAction != null) {
      popup.setAdText((rootAction).getCaption());
    }
    return popup;
  }

  @Nullable
  private static RootAction getRootAction(Object value) {
    if (value instanceof PopupFactoryImpl.ActionItem) {
      AnAction action = ((PopupFactoryImpl.ActionItem)value).getAction();
      if (action instanceof RootAction) {
        return (RootAction)action;
      }
    }
    return null;
  }

  @Override
  protected ListCellRenderer getListElementRenderer() {
    return new PopupListElementRenderer(this) {

      private ErrorLabel myBranchLabel;

      @Override
      protected void customizeComponent(JList list, Object value, boolean isSelected) {
        super.customizeComponent(list, value, isSelected);

        RootAction rootAction = getRootAction(value);
        if (rootAction != null) {
          myBranchLabel.setVisible(true);
          myBranchLabel.setText(String.format("[%s]", rootAction.getDisplayableBranchText()));

          if (isSelected) {
            setSelected(myBranchLabel);
          }
          else {
            myBranchLabel.setBackground(getBackground());
            myBranchLabel.setForeground(JBColor.GRAY);    // different foreground than for other elements
          }
        }
        else {
          myBranchLabel.setVisible(false);
        }
      }

      @Override
      protected JComponent createItemComponent() {
        myTextLabel = new ErrorLabel();
        myTextLabel.setOpaque(true);
        myTextLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        myBranchLabel = new ErrorLabel();
        myBranchLabel.setOpaque(true);
        myBranchLabel.setBorder(BorderFactory.createEmptyBorder(1, UIUtil.DEFAULT_HGAP, 1, 1));

        JPanel compoundPanel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
        compoundPanel.add(myTextLabel, BorderLayout.CENTER);
        compoundPanel.add(myBranchLabel, BorderLayout.EAST);

        return layoutComponent(compoundPanel);
      }
    };
  }
}
