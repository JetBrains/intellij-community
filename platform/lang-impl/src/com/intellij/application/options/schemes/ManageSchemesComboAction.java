/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.application.options.schemes.DefaultSchemeActions;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class ManageSchemesComboAction<T extends Scheme> extends ComboBoxAction {
  
  private Collection<AnAction> myActions;

  public ManageSchemesComboAction(@NotNull DefaultSchemeActions<T> schemeActions) {
    myActions = schemeActions.getActions();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    for(AnAction action : myActions) {
      group.add(action);
    }
    return group;
  }
  
  @Override
  public void update(AnActionEvent e) {
    for(AnAction action : myActions) {
      action.update(e);
    }
  }
  
  public JButton createCombo() {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.General.GearPlain);
    return createComboBoxButton(presentation);
  }
  
}
