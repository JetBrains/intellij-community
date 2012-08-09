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
import com.intellij.application.options.codeStyle.arrangement.renderer.ArrangementTreeRenderer;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Generic GUI for showing standard arrangement settings.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 12:43 PM
 */
public abstract class ArrangementSettingsPanel extends CodeStyleAbstractPanel {

  @NotNull private final JPanel myContent = new JPanel(new GridBagLayout());
  @NotNull private final ArrangementStandardSettingsAware myFilter;

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull ArrangementStandardSettingsAware filter) {
    super(settings);
    myFilter = filter;
    init();
  }

  private void init() {
    final ArrangementTreeRenderer renderer = new ArrangementTreeRenderer(myFilter);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    Tree tree = new Tree(model) {
      @Override
      protected void setExpandedState(TreePath path, boolean state) {
        // Don't allow node collapse
        if (state) {
          super.setExpandedState(path, state);
        }
      }

      @Override
      public void setSelectionPath(TreePath path) {
        // Don't allow selection in order to avoid standard selection background drawing.
      }

      @Override
      public void paint(Graphics g) {
        renderer.onTreeRepaintStart();
        super.paint(g);
      }
    };

    // TODO den remove
    List<ArrangementSettingsNode> children = new ArrayList<ArrangementSettingsNode>();
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC));
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC));
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL));

    HierarchicalArrangementSettingsNode settingsNode = new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.TYPE, ArrangementEntryType.FIELD
    ));
    ArrangementSettingsCompositeNode modifiers = new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND);
    for (ArrangementSettingsNode child : children) {
      modifiers.addOperand(child);
    }
    settingsNode.addChild(new HierarchicalArrangementSettingsNode(modifiers));
    //ArrangementSettingsNode node = ArrangementSettingsUtil.buildTreeStructure(settingsNode);
    if (settingsNode != null) {
      map(root, settingsNode);
    }

    expandAll(tree, new TreePath(root));
    tree.setRootVisible(false);
    tree.setShowsRootHandles(false);
    tree.setCellRenderer(renderer);

    myContent.add(tree, new GridBag().weightx(1).weighty(1).fillCell());
  }

  private static void map(@NotNull DefaultMutableTreeNode parentTreeNode, @NotNull HierarchicalArrangementSettingsNode settingsNode) {
    DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(settingsNode);
    parentTreeNode.add(childTreeNode);
    for (HierarchicalArrangementSettingsNode node : settingsNode.getChildren()) {
      map(childTreeNode, node);
    }
  }

  private static void expandAll(Tree tree, TreePath parent) {
    // Traverse children
    TreeNode node = (TreeNode)parent.getLastPathComponent();
    if (node.getChildCount() >= 0) {
      for (Enumeration e = node.children(); e.hasMoreElements(); ) {
        TreeNode n = (TreeNode)e.nextElement();
        TreePath path = parent.pathByAddingChild(n);
        expandAll(tree, path);
      }
    }

    // Expansion or collapse must be done bottom-up
    tree.expandPath(parent);
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
    return ApplicationBundle.message("tab.title.arrangement");
  }
}
