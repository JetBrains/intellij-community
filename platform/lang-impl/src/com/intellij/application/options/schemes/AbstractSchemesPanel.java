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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public abstract class AbstractSchemesPanel<T extends Scheme> {
  
  private ComboBox mySchemesCombo;
  private JPanel myPanel;
  private JPanel myToolbarPanel;

  public AbstractSchemesPanel() {
    myToolbarPanel.add(createToolbar());
  }

  public JPanel getRootPanel() {
    return myPanel;
  }

  public JPanel getToolbarPanel() {
    return myToolbarPanel;
  }

  private void createUIComponents() {
    mySchemesCombo = createSchemesCombo();
  }
  
  private JComponent createToolbar() {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    toolbarActionGroup.add(new TopActionGroup());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, toolbarActionGroup, true);
    return toolbar.getComponent();
  }


  private class TopActionGroup extends ActionGroup implements DumbAware {
    public TopActionGroup() {
      super("", true);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      Collection<AnAction> actions = createSchemeActions().getActions();
      return actions.toArray(new AnAction[actions.size()]);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(AllIcons.General.GearPlain);
    }
  }
  
  protected abstract ComboBox createSchemesCombo();
  
  protected abstract DefaultSchemeActions<T> createSchemeActions();

  public ComboBox getSchemesCombo() {
    return mySchemesCombo;
  }
  
  public void disposeUIResources() {
    myPanel.removeAll();
  }
}
