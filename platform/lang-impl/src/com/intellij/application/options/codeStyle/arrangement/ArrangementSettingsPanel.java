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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GridBag;
import org.jdesktop.swingx.JXTaskPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;

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
    final ArrangementRuleTree ruleTree = new ArrangementRuleTree(filter);
    Tree component = ruleTree.getTreeComponent();
    myContent.add(new JBScrollPane(component), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    CustomizationUtil.installPopupHandler(
      component, ArrangementConstants.ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU, ArrangementConstants.RULE_EDITOR_PLACE
    );

    final JXTaskPane editorPane = new JXTaskPane(ApplicationBundle.message("arrangement.title.editor"));
    final ArrangementMatcherRuleEditor ruleEditor = new ArrangementMatcherRuleEditor(filter);
    editorPane.add(ruleEditor);
    editorPane.setCollapsed(true);
    myContent.add(editorPane, new GridBag().weightx(1).fillCellHorizontally().coverLine());
    
    ruleTree.addEditingListener(new ArrangementMatcherEditingListener() {
      @Override
      public void startEditing(@NotNull ArrangementMatcherSettings settings) {
        ruleEditor.updateState(settings);
        editorPane.setCollapsed(false); 
      }

      @Override
      public void stopEditing() {
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
