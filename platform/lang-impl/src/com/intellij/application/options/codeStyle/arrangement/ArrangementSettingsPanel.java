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
import com.intellij.application.options.codeStyle.arrangement.action.AddArrangementRuleAction;
import com.intellij.application.options.codeStyle.arrangement.action.RemoveArrangementRuleAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic GUI for showing standard arrangement settings.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 12:43 PM
 */
public abstract class ArrangementSettingsPanel extends CodeStyleAbstractPanel {

  @NotNull private final JPanel myContent = new JPanel(new GridBagLayout());

  @NotNull private final Language                         myLanguage;
  @NotNull private final ArrangementStandardSettingsAware mySettingsAware;
  @NotNull private final ArrangementRuleTree              myRuleTree;

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    super(settings);
    myLanguage = language;
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);
    assert rearranger instanceof ArrangementStandardSettingsAware;
    mySettingsAware = (ArrangementStandardSettingsAware)rearranger;
    ArrangementStandardSettingsRepresentationAware representationManager = DefaultArrangementSettingsRepresentationManager.INSTANCE;
    if (mySettingsAware instanceof ArrangementStandardSettingsRepresentationAware) {
      representationManager = (ArrangementStandardSettingsRepresentationAware)mySettingsAware;
    }
    final ArrangementNodeDisplayManager displayManager = new ArrangementNodeDisplayManager(mySettingsAware, representationManager);
    ArrangementConditionsGrouper grouper = DefaultArrangementSettingsGrouper.INSTANCE;
    if (mySettingsAware instanceof ArrangementConditionsGrouper) {
      grouper = (ArrangementConditionsGrouper)mySettingsAware;
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ArrangementConstants.ACTION_GROUP_RULE_EDITOR_TOOL_WINDOW);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(ArrangementConstants.RULE_EDITOR_TOOL_WINDOW_PLACE, actionGroup, true);
    JPanel toolbarControl = new JPanel(new GridBagLayout());
    toolbarControl.add(actionToolbar.getComponent(), new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally());
    toolbarControl.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP | SideBorder.RIGHT));
    myContent.add(toolbarControl, new GridBag().weightx(1).fillCellHorizontally().coverLine());

    myRuleTree = new ArrangementRuleTree(getRules(settings), grouper, displayManager);
    final Tree treeComponent = myRuleTree.getTreeComponent();
    actionToolbar.setTargetComponent(treeComponent);
    myContent.add(new JBScrollPane(treeComponent), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    CustomizationUtil.installPopupHandler(
      treeComponent, ArrangementConstants.ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU, ArrangementConstants.RULE_EDITOR_PLACE
    );

    final JXTaskPane editorPane = new JXTaskPane(ApplicationBundle.message("arrangement.title.editor"));
    final ArrangementRuleEditor ruleEditor = new ArrangementRuleEditor(mySettingsAware, displayManager);
    ruleEditor.applyBackground(treeComponent.getBackground());
    editorPane.getContentPane().setBackground(treeComponent.getBackground());
    editorPane.add(ruleEditor);
    editorPane.setCollapsed(true);
    myContent.add(editorPane, new GridBag().weightx(1).fillCellHorizontally().coverLine());
    final Ref<Boolean> resetEditor = new Ref<Boolean>(Boolean.TRUE);
    linkTreeAndEditor(editorPane, ruleEditor, resetEditor);
    setupRuleManagementActions(treeComponent, editorPane, ruleEditor, resetEditor);
    setupKeyboardActions(actionManager, treeComponent);
  }

  private void linkTreeAndEditor(@NotNull final JXTaskPane editorPane,
                                 @NotNull final ArrangementRuleEditor ruleEditor,
                                 @NotNull final Ref<Boolean> resetEditor)
  {
    editorPane.addPropertyChangeListener("collapsed", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() == Boolean.FALSE && resetEditor.get()) {
          ruleEditor.updateState(null);
        }
      }
    });
    myRuleTree.addEditingListener(new ArrangementRuleSelectionListener() {
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

  private void setupRuleManagementActions(@NotNull final Tree treeComponent,
                                          @NotNull final JXTaskPane editorPane,
                                          @NotNull final ArrangementRuleEditor ruleEditor,
                                          @NotNull final Ref<Boolean> resetEditor)
  {
    final Runnable newRuleFunction = new Runnable() {
      @Override
      public void run() {
        treeComponent.requestFocus();
        ArrangementRuleEditingModel model = myRuleTree.newModel();
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
    final Runnable removeRuleFunction = new Runnable() {
      @Override
      public void run() {
        treeComponent.requestFocus();
        ArrangementRuleEditingModelImpl model = myRuleTree.getActiveModel();
        if (model != null) {
          model.destroy();
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
        else if (ArrangementConstants.REMOVE_RULE_FUNCTION_KEY.is(dataId)) {
          return removeRuleFunction;
        }
        return null;
      }
    });
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private List<ArrangementRule<StdArrangementEntryMatcher>> getRules(@NotNull CodeStyleSettings settings) {
    List<ArrangementRule<?>> storedRules = settings.getCommonSettings(myLanguage).getArrangementRules();
    if (storedRules.isEmpty()) {
      List<ArrangementRule<StdArrangementEntryMatcher>> defaultRules = mySettingsAware.getDefaultRules();
      if (defaultRules != null) {
        return defaultRules;
      }
    }
    else {
      // We use unchecked cast here in assumption that current rearranger is based on standard settings if it uses standard
      // settings-based rule editor.
      // Note: unchecked cast for the whole collection doesn't work here (compiler error).
      List<ArrangementRule<StdArrangementEntryMatcher>> result = new ArrayList<ArrangementRule<StdArrangementEntryMatcher>>();
      for (ArrangementRule<?> rule : storedRules) {
        result.add((ArrangementRule<StdArrangementEntryMatcher>)rule);
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static void setupKeyboardActions(@NotNull ActionManager actionManager, @NotNull Tree treeComponent) {
    List<AnAction> keyboardActions = new ArrayList<AnAction>();
    
    AnAction newRuleAction = new AddArrangementRuleAction();
    newRuleAction.copyFrom(actionManager.getAction("Arrangement.Rule.Add"));
    newRuleAction.registerCustomShortcutSet(CommonShortcuts.ENTER, treeComponent);
    keyboardActions.add(newRuleAction);

    AnAction removeRuleAction = new RemoveArrangementRuleAction();
    removeRuleAction.copyFrom(actionManager.getAction("Arrangement.Rule.Remove"));
    removeRuleAction.registerCustomShortcutSet(CommonShortcuts.DELETE, treeComponent);
    keyboardActions.add(removeRuleAction);
    
    treeComponent.putClientProperty(AnAction.ourClientProperty, keyboardActions);
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    // TODO den implement 
    return null;
  }

  @Override
  public boolean isModified(@NotNull CodeStyleSettings settings) {
    return !getRules(settings).equals(myRuleTree.getRules());
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getCommonSettings(myLanguage).setArrangementRules(myRuleTree.getRules()); 
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myRuleTree.setRules(getRules(settings)); 
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
