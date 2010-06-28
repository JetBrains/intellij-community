/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 29-Mar-2006
 */
public class DefaultConfigurationSettingsEditor implements Configurable {

  @NonNls private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private final Tree myTree = new Tree(myRoot);
  private final Project myProject;
  private final Map<ConfigurationType, Configurable> myStoredComponents = new HashMap<ConfigurationType, Configurable>();
  private final ConfigurationType mySelection;

  public DefaultConfigurationSettingsEditor(Project project, ConfigurationType selection) {
    myProject = project;
    mySelection = selection;
  }

  public JComponent createComponent() {
    final JPanel wholePanel = new JPanel(new BorderLayout());
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    wholePanel.add(pane, BorderLayout.WEST);
    final JPanel rightPanel = new JPanel(new BorderLayout());
    wholePanel.add(rightPanel, BorderLayout.CENTER);
    final ConfigurationType[] configurationTypes = RunManagerImpl.getInstanceImpl(myProject).getConfigurationFactories();
    for (ConfigurationType type : configurationTypes) {
      myRoot.add(new DefaultMutableTreeNode(type));
    }
    myTree.setRootVisible(false);
    TreeUtil.installActions(myTree);
    myTree.setCellRenderer(new DefaultTreeCellRenderer() {
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        final Component rendererComponent = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof ConfigurationType) {
            final ConfigurationType type = (ConfigurationType)userObject;
            setText(type.getDisplayName());
            setIcon(type.getIcon());
          }
        }
        return rendererComponent;
      }
    });
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final ConfigurationType type = (ConfigurationType)node.getUserObject();
          rightPanel.removeAll();
          Configurable configurable = myStoredComponents.get(type);
          if (configurable == null){
            configurable = TypeTemplatesConfigurable.createConfigurable(type, myProject);
            myStoredComponents.put(type, configurable);
            rightPanel.add(configurable.createComponent());
            configurable.reset();
          } else {
            rightPanel.add(configurable.createComponent());
          }
          rightPanel.revalidate();
          rightPanel.repaint();
          final Window window = SwingUtilities.windowForComponent(wholePanel);
          if (window != null &&
              (window.getSize().height < window.getMinimumSize().height ||
               window.getSize().width < window.getMinimumSize().width)) {
            window.pack();
          }
        }
      }
    });
    RunConfigurable.sortTree(myRoot);
    ((DefaultTreeModel)myTree.getModel()).reload();
    TreeUtil.selectFirstNode(myTree);
    TreeUtil.traverse(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof DefaultMutableTreeNode){
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object o = treeNode.getUserObject();
          if (Comparing.equal(o, mySelection)){
            TreeUtil.selectInTree(treeNode, true, myTree);
            return false;
          }
        }
        return true;
      }
    });
    return wholePanel;
  }

  public boolean isModified() {
    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()){
        configurable.apply();
      }
    }
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    for (Configurable configurable : myStoredComponents.values()) {
      configurable.disposeUIResources();
    }
  }

  public String getDisplayName() {
    return ExecutionBundle.message("default.settings.editor.dialog.title");
  }

  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }
}
