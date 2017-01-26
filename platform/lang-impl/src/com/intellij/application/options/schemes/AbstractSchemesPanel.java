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
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public abstract class AbstractSchemesPanel<T extends Scheme> extends JPanel {
  
  private SchemesCombo<T> mySchemesCombo;
  private AbstractSchemeActions<T> myActions;
  private JComponent myToolbar;
  private JLabel myInfoLabel;

  public AbstractSchemesPanel() {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    createUIComponents();
  }
  
  private void createUIComponents() {
    JPanel controlsPanel = new JPanel();
    controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.LINE_AXIS));
    controlsPanel.add(new JLabel(ApplicationBundle.message("editbox.scheme.name")));
    controlsPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    myActions = createSchemeActions();
    mySchemesCombo = new SchemesCombo<>(this);
    controlsPanel.add(mySchemesCombo.getComponent());
    myToolbar = createToolbar();
    controlsPanel.add(myToolbar);
    myInfoLabel = new JLabel();
    controlsPanel.add(myInfoLabel);
    controlsPanel.add(Box.createHorizontalGlue());
    controlsPanel.setMaximumSize(new Dimension(controlsPanel.getMaximumSize().width, mySchemesCombo.getComponent().getPreferredSize().height));
    add(controlsPanel);
    add(Box.createVerticalGlue());
    add(Box.createRigidArea(new Dimension(0, 10)));
  }
  
  private JComponent createToolbar() {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    toolbarActionGroup.add(new TopActionGroup());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, toolbarActionGroup, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setMaximumSize(new Dimension(toolbarComponent.getPreferredSize().width, Short.MAX_VALUE));
    return toolbarComponent;
  }


  private class TopActionGroup extends ActionGroup implements DumbAware {
    public TopActionGroup() {
      super("", true);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      Collection<AnAction> actions = myActions.getActions();
      return actions.toArray(new AnAction[actions.size()]);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(AllIcons.General.GearPlain);
    }
  }

  public JComponent getToolbar() {
    return myToolbar;
  }

  protected abstract AbstractSchemeActions<T> createSchemeActions();
  
  public T getSelectedScheme() {
    return mySchemesCombo.getSelectedScheme();
  }
  
  public void selectScheme(@Nullable T scheme) {
    mySchemesCombo.selectScheme(scheme);
  }
  
  public void resetSchemes(@NotNull Collection<T> schemes) {
    mySchemesCombo.resetSchemes(schemes);
  }
  
  public void disposeUIResources() {
    removeAll();
  }
  
  public void startEdit() {
    mySchemesCombo.startEdit();
  }
  
  public void cancelEdit() {
    mySchemesCombo.cancelEdit();
  }

  public void showInfo(@Nullable String message, @NotNull MessageType messageType) {
    myInfoLabel.setText(message);
    myInfoLabel.setForeground(messageType.getTitleForeground());
  }

  public void clearInfo() {
    myInfoLabel.setText(null);
  }

  public AbstractSchemeActions<T> getActions() {
    return myActions;
  }

  @NotNull
  public abstract SchemesModel<T> getModel();

  public void updateOnCurrentSettingsChange() {
    mySchemesCombo.updateSelected();
  }
}
