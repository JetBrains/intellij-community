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
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.settings.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GridBag;
import org.jdesktop.swingx.JXTaskPane;
import org.jetbrains.annotations.NotNull;

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

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull ArrangementStandardSettingsAware filter) {
    super(settings);
    ArrangementStandardSettingsRepresentationAware representationManager = DefaultArrangementSettingsRepresentationManager.INSTANCE;
    if (filter instanceof ArrangementStandardSettingsRepresentationAware) {
      representationManager = (ArrangementStandardSettingsRepresentationAware)filter;
    }
    ArrangementNodeDisplayManager displayManager = new ArrangementNodeDisplayManager(filter, representationManager);
    ArrangementSettingsGrouper grouper = DefaultArrangementSettingsGrouper.INSTANCE;
    if (filter instanceof ArrangementSettingsGrouper) {
      grouper = (ArrangementSettingsGrouper)filter;
    }
    
    final ArrangementRuleTree ruleTree = new ArrangementRuleTree(grouper, displayManager);
    Tree component = ruleTree.getTreeComponent();
    myContent.add(new JBScrollPane(component), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    CustomizationUtil.installPopupHandler(
      component, ArrangementConstants.ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU, ArrangementConstants.RULE_EDITOR_PLACE
    );

    final JXTaskPane editorPane = new JXTaskPane(ApplicationBundle.message("arrangement.title.editor"));
    final ArrangementMatchConditionEditor ruleEditor = new ArrangementMatchConditionEditor(filter, displayManager);
    ruleEditor.applyBackground(component.getBackground());
    editorPane.getContentPane().setBackground(component.getBackground());
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
