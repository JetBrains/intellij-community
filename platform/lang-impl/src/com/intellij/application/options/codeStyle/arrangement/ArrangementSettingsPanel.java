/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.settings.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GridBag;
import org.jdesktop.swingx.JXTaskPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Generic GUI for showing standard arrangement settings.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 12:43 PM
 */
public abstract class ArrangementSettingsPanel extends CodeStyleAbstractPanel {

  @NotNull private final JPanel myContent = new JPanel(new GridBagLayout());

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull final ArrangementStandardSettingsAware filter) {
    super(settings);
    ArrangementStandardSettingsRepresentationAware representationManager = DefaultArrangementSettingsRepresentationManager.INSTANCE;
    if (filter instanceof ArrangementStandardSettingsRepresentationAware) {
      representationManager = (ArrangementStandardSettingsRepresentationAware)filter;
    }
    final ArrangementNodeDisplayManager displayManager = new ArrangementNodeDisplayManager(filter, representationManager);
    ArrangementSettingsGrouper grouper = DefaultArrangementSettingsGrouper.INSTANCE;
    if (filter instanceof ArrangementSettingsGrouper) {
      grouper = (ArrangementSettingsGrouper)filter;
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ArrangementConstants.ACTION_GROUP_RULE_EDITOR_TOOL_WINDOW);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(ArrangementConstants.RULE_EDITOR_TOOL_WINDOW_PLACE, actionGroup, true);
    JPanel toolbarControl = new JPanel(new GridBagLayout());
    toolbarControl.add(actionToolbar.getComponent(), new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally());
    toolbarControl.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP | SideBorder.RIGHT));
    myContent.add(toolbarControl, new GridBag().weightx(1).fillCellHorizontally().coverLine());
    
    final ArrangementRuleTree ruleTree = new ArrangementRuleTree(grouper, displayManager);
    final Tree treeComponent = ruleTree.getTreeComponent();
    actionToolbar.setTargetComponent(treeComponent);
    myContent.add(new JBScrollPane(treeComponent), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    CustomizationUtil.installPopupHandler(
      treeComponent, ArrangementConstants.ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU, ArrangementConstants.RULE_EDITOR_PLACE
    );

    final JXTaskPane editorPane = new JXTaskPane(ApplicationBundle.message("arrangement.title.editor"));
    final ArrangementRuleEditor ruleEditor = new ArrangementRuleEditor(filter, displayManager);
    ruleEditor.applyBackground(treeComponent.getBackground());
    editorPane.getContentPane().setBackground(treeComponent.getBackground());
    editorPane.add(ruleEditor);
    editorPane.setCollapsed(true);
    final Ref<Boolean> resetEditor = new Ref<Boolean>(Boolean.TRUE);
    myContent.add(editorPane, new GridBag().weightx(1).fillCellHorizontally().coverLine());
    editorPane.addPropertyChangeListener("collapsed", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() == Boolean.FALSE && resetEditor.get()) {
          ruleEditor.updateState(null);
        }
      }
    });
    ruleTree.addEditingListener(new ArrangementRuleSelectionListener() {
      @Override
      public void onSelected(@NotNull ArrangementRuleEditingModel model) {
        ruleEditor.updateState(model);
        resetEditor.set(Boolean.FALSE);
        try {
          editorPane.setCollapsed(false);
        }
        finally {
          resetEditor.set(Boolean.TRUE);
        }
      }

      @Override
      public void selectionRemoved() {
        editorPane.setCollapsed(true); 
      }
    });
    final Runnable newRuleFunction = new Runnable() {
      @Override
      public void run() {
        treeComponent.requestFocus();
        ArrangementRuleEditingModel model = ruleTree.newModel();
        ruleEditor.updateState(model);
        resetEditor.set(Boolean.FALSE);
        try {
          editorPane.setCollapsed(false);
        }
        finally {
          resetEditor.set(Boolean.TRUE);
        }
      }
    };
    treeComponent.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (ArrangementConstants.NEW_RULE_FUNCTION_KEY.is(dataId)) {
          return newRuleFunction;
        }
        return null;
      }
    });    
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    // TODO den implement 
    return null;
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    // TODO den implement 
    return false;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    // TODO den implement 
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    // TODO den implement 
  }

  @Override
  public JComponent getPanel() {
    return myContent;
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("arrangement.title.settings.tab");
  }
}
