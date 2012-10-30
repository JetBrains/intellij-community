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
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementSectionNode;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.settings.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.GridBag;
import org.jdesktop.swingx.JXTaskPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    
    List<Set<ArrangementMatchCondition>> groupingRules = Collections.emptyList();
    if (mySettingsAware instanceof ArrangementConditionsGrouper) {
      groupingRules = ((ArrangementConditionsGrouper)mySettingsAware).getGroupingConditions();
    }

    final ArrangementColorsProvider colorsProvider;
    if (rearranger instanceof ArrangementColorsAware) {
      colorsProvider = new ArrangementColorsProviderImpl((ArrangementColorsAware)rearranger);
    }
    else {
      colorsProvider = new ArrangementColorsProviderImpl(null);
    }
    
    final ArrangementNodeDisplayManager displayManager = new ArrangementNodeDisplayManager(
      mySettingsAware, colorsProvider, representationManager
    );

    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ArrangementConstants.ACTION_GROUP_RULE_EDITOR_TOOL_WINDOW);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(ArrangementConstants.RULE_EDITOR_TOOL_WINDOW_PLACE, actionGroup, true);
    JPanel toolbarControl = new JPanel(new GridBagLayout());
    toolbarControl.add(actionToolbar.getComponent(), new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally());
    toolbarControl.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP | SideBorder.RIGHT));
    myContent.add(toolbarControl, new GridBag().weightx(1).fillCellHorizontally().coverLine());

    myRuleTree = new ArrangementRuleTree(getSettings(settings), groupingRules, displayManager, colorsProvider, mySettingsAware);
    final Tree treeComponent = myRuleTree.getTreeComponent();
    actionToolbar.setTargetComponent(treeComponent);
    JBScrollPane scrollPane = new JBScrollPane(treeComponent);
    myContent.add(scrollPane, new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    CustomizationUtil.installPopupHandler(
      treeComponent, ArrangementConstants.ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU, ArrangementConstants.RULE_EDITOR_PLACE
    );

    final JXTaskPane editorPane = new JXTaskPane(ApplicationBundle.message("arrangement.title.editor"));
    final ArrangementRuleEditor ruleEditor = new ArrangementRuleEditor(mySettingsAware, colorsProvider, displayManager);
    ruleEditor.applyBackground(treeComponent.getBackground());
    editorPane.getContentPane().setBackground(treeComponent.getBackground());
    editorPane.add(ruleEditor);
    editorPane.setCollapsed(true);
    myContent.add(editorPane, new GridBag().weightx(1).fillCellHorizontally().coverLine());
    final Ref<Boolean> resetEditor = new Ref<Boolean>(Boolean.TRUE);
    linkTreeAndEditor(editorPane, ruleEditor, resetEditor);
    setupRuleManagementActions(treeComponent, editorPane, ruleEditor, resetEditor);
    setupKeyboardActions(actionManager, treeComponent);

    setupScrollingHelper(treeComponent, scrollPane, editorPane);
    setupCanvasWidthUpdater(scrollPane);
  }

  private void setupCanvasWidthUpdater(@NotNull JBScrollPane scrollPane) {
    final JViewport viewport = scrollPane.getViewport();
    viewport.addChangeListener(new ChangeListener() {

      private int myWidth;

      @Override
      public void stateChanged(ChangeEvent e) {
        Rectangle visibleRect = viewport.getVisibleRect();
        if (visibleRect == null || visibleRect.width <= 0) {
          return;
        }
        if (myWidth != visibleRect.width) {
          myWidth = visibleRect.width;
          myRuleTree.updateCanvasWidth(myWidth);
        }
      }
    });
  }
  
  /**
   * The general idea is to configure UI in a way that it automatically changes tree viewport 'y' coordinate in order to make
   * target rule visible on rule editor opening.
   * <p/>
   * Example: target rule is located at the bottom of the tree, so, it's closed by the rule editor when it's open.
   * 
   * @param treeComponent  target tree
   * @param scrollPane     scroll panel which holds given tree
   * @param editorPane     editor component
   */
  private void setupScrollingHelper(@NotNull final Tree treeComponent,
                                    @NotNull JBScrollPane scrollPane,
                                    @NotNull final JXTaskPane editorPane)
  {
    final JViewport viewport = scrollPane.getViewport();
    viewport.addChangeListener(new ChangeListener() {

      private boolean mySkip;
      private int myYToRestore;

      @Override
      public void stateChanged(ChangeEvent e) {
        if (mySkip) {
          return;
        }
        myYToRestore = -1;
        if (editorPane.isCollapsed()) {
          if (myYToRestore >= 0) {
            scroll(myYToRestore);
            myYToRestore = -1;
          }
        }
        else {
          myYToRestore = -1;
          List<ArrangementRuleEditingModelImpl> models = myRuleTree.getActiveModels();
          if (models.size() == 1) {
            Rectangle bounds = treeComponent.getPathBounds(new TreePath(models.get(0).getBottomMost().getPath()));
            if (bounds != null) {
              myYToRestore = bounds.y;
              Rectangle viewRect = viewport.getViewRect();
              if (bounds.y < viewRect.y) {
                scroll(bounds.y);
              }
              else if (bounds.y + bounds.height >= viewRect.y + viewRect.height) {
                scroll(bounds.y + bounds.height - viewRect.height);
              }
            }
          }
        }
      }

      private void scroll(int y) {
        mySkip = true;
        try {
          viewport.setViewPosition(new Point(0, y));
        }
        finally {
          mySkip = false;
        }
      }
    });
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
      public void onSelectionChange(@NotNull List<? extends ArrangementRuleEditingModel> selectedModels) {
        if (selectedModels.size() != 1) {
          editorPane.setCollapsed(true);
        }
        else {
          ruleEditor.updateState(selectedModels.get(0));
          resetEditor.set(Boolean.FALSE);
          try {
            editorPane.setCollapsed(false);
          }
          finally {
            resetEditor.set(Boolean.TRUE);
          }
        }
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
        List<ArrangementRuleEditingModelImpl> models = myRuleTree.getActiveModels();
        for (ArrangementRuleEditingModelImpl model : models) {
          model.destroy();
        }
      }
    };
    final NotNullFunction<Boolean, Boolean> updateMoveFunction = new NotNullFunction<Boolean, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(@NotNull Boolean moveUp) {
        TreePath[] paths = treeComponent.getSelectionPaths();
        if (paths == null) {
          return false;
        }
        for (TreePath path : paths) {
          TreeNode node = (TreeNode)path.getLastPathComponent();
          if (node instanceof ArrangementSectionNode) {
            continue;
          }
          if ((moveUp && node.getParent().getIndex(node) > 0)
              || (!moveUp && node.getParent().getIndex(node) < node.getParent().getChildCount() - 1))
          {
            return true;
          }
        }
        return false;
      }
    };
    final Consumer<Boolean/* move up? */> moveFunction = new Consumer<Boolean>() {
      @Override
      public void consume(Boolean moveUp) {
        TreePath[] paths = treeComponent.getSelectionPaths();
        // TODO den implement
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
        // TODO den uncomment
        //else if (ArrangementConstants.UPDATE_MOVE_RULE_FUNCTION_KEY.is(dataId)) {
        //  return updateMoveFunction;
        //}
        return null;
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private StdArrangementSettings getSettings(@NotNull CodeStyleSettings settings) {
    StdArrangementSettings result = (StdArrangementSettings)settings.getCommonSettings(myLanguage).getArrangementSettings();
    if (result == null) {
      result = mySettingsAware.getDefaultSettings();
    }
    return result;
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
    return !Comparing.equal(getSettings(settings), myRuleTree.getSettings());
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getCommonSettings(myLanguage).setArrangementSettings(myRuleTree.getSettings());
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myRuleTree.setSettings(getSettings(settings));
  }

  @Override
  public JComponent getPanel() {
    return myContent;
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("arrangement.title.settings.tab");
  }

  @Override
  public void dispose() {
    super.dispose();
    myRuleTree.disposeUI();
  }
}
