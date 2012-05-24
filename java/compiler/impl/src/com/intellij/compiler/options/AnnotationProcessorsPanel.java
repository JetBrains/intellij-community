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
package com.intellij.compiler.options;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked", "UseOfObsoleteCollectionType"})
public class AnnotationProcessorsPanel extends JPanel {
  private final Map<String, List<Module>> profiles = new HashMap<String, List<Module>>();
  private static final String DEFAULT_PROFILE = "Default";
  private final Project myProject;
  private final Tree myTree;
  private JPanel myContentPanel;

  public AnnotationProcessorsPanel(Project project) {
    super(new BorderLayout());
    myProject = project;
    loadProfiles();
    myTree = new Tree(new MyTreeModel());
    myTree.setRootVisible(false);
    final JPanel treePanel =
      ToolbarDecorator.createDecorator(myTree).addExtraAction(new AnActionButton("Move to", IconLoader.getIcon("/actions/nextfile.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final MyModuleNode node = (MyModuleNode)myTree.getSelectionPath().getLastPathComponent();
          final TreePath[] selectedNodes = myTree.getSelectionPaths();
          final String key = ((MyProfileNode)node.getParent()).myKey;
          final List<String> profileNames = new ArrayList<String>();
          profileNames.add(DEFAULT_PROFILE);
          profileNames.addAll(profiles.keySet());
          profileNames.remove(key);
          final JBList list = new JBList(profileNames);
          final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setTitle("Move to")
            .setItemChoosenCallback(new Runnable() {
              @Override
              public void run() {
                final Object value = list.getSelectedValue();
                if (value instanceof String) {
                  final Module toSelect = (Module)node.getUserObject();
                  if (selectedNodes != null) {
                    for (TreePath selectedNode : selectedNodes) {
                      final Object n = selectedNode.getLastPathComponent();

                      if (n instanceof MyModuleNode) {
                        Module module = (Module)((MyModuleNode)n).getUserObject();
                        if (!DEFAULT_PROFILE.equals(key)) {
                          profiles.get(key).remove(module);
                        }
                        if (!DEFAULT_PROFILE.equals(value)) {
                          profiles.get(value).add(module);
                        }
                      }
                    }
                  }

                  final MyRootNode root = (MyRootNode)myTree.getModel().getRoot();
                  root.sync();
                  final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, toSelect);
                  if (node != null) {
                    TreeUtil.selectNode(myTree, node);
                  }
                }
              }
            })
            .createPopup();
          RelativePoint point = e.getInputEvent() instanceof MouseEvent ? getPreferredPopupPoint() : TreeUtil.getPointForSelection(myTree);
          popup.show(point);
        }

        @Override
        public ShortcutSet getShortcut() {
          return ActionManager.getInstance().getAction("Move").getShortcutSet();
        }

        @Override
        public boolean isEnabled() {
          return myTree.getSelectionPath() != null
                 && myTree.getSelectionPath().getLastPathComponent() instanceof MyModuleNode
                 && !profiles.isEmpty();
        }
      }).createPanel();
    add(treePanel, BorderLayout.WEST);
    myTree.setCellRenderer(new MyCellRenderer());
    ((MyRootNode)myTree.getModel().getRoot()).sync();
    myContentPanel = new JPanel(new BorderLayout());
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      String currentProfile = null;
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          Object node = path.getLastPathComponent();
          if (node instanceof MyModuleNode) {
            node = ((MyModuleNode)node).getParent();
          }
          if (node instanceof MyProfileNode) {
            if (!StringUtil.equals(currentProfile, ((MyProfileNode)node).myKey)) {
              currentProfile = ((MyProfileNode)node).myKey;
              myContentPanel.removeAll();
              myContentPanel.add(getComponentForProfile(currentProfile), BorderLayout.CENTER);
              revalidate();
              repaint();
            }
          }
        }
      }
    });
    add(myContentPanel, BorderLayout.CENTER);
  }


  private JComponent getComponentForProfile(String profile) {
    //TODO[jeka] correct panel
    return new JLabel(profile, SwingConstants.CENTER);
  }

  private static void expand(JTree tree) {
    int oldRowCount = 0;
    do {
      int rowCount = tree.getRowCount();
      if (rowCount == oldRowCount) break;
      oldRowCount = rowCount;
      for (int i = 0; i < rowCount; i++) {
        tree.expandRow(i);
      }
    }
    while (true);
  }

  private void loadProfiles() {
    //TODO[jeka] init profiles map
  }

  private class MyTreeModel extends DefaultTreeModel implements EditableTreeModel{
    public MyTreeModel() {
      super(new MyRootNode());
    }

    @Override
    public TreePath addNode(TreePath parentOrNeighbour) {
      final String profile = Messages.showInputDialog(myProject, "Profile name", "Create new profile", null, "", new InputValidatorEx() {
        @Override
        public boolean checkInput(String inputString) {
          return !DEFAULT_PROFILE.equals(inputString) && !profiles.containsKey(inputString) && !StringUtil.isEmpty(inputString);
        }

        @Override
        public boolean canClose(String inputString) {
          return checkInput(inputString);
        }

        @Override
        public String getErrorText(String inputString) {
          if (checkInput(inputString)) return null;
          return StringUtil.isEmpty(inputString) ? "Profile name shouldn't be empty"
                                                 : "Profile " + inputString + " already exists";
        }
      });
      if (profile != null) {
        profiles.put(profile, new ArrayList<Module>());
      }
      ((SyncWithMap)getRoot()).sync();
      final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), profile);
      if (object != null) {
        TreeUtil.selectNode(myTree, object);
      }
      return null;
    }

    @Override
    public void removeNode(TreePath parent) {
    }

    @Override
    public void moveNodeTo(TreePath parentOrNeighbour) {
    }

  }


  private class MyRootNode extends DefaultMutableTreeNode implements SyncWithMap {
    @Override
    public SyncWithMap sync() {
      final Vector newKids =  new Vector();
      for (String key : profiles.keySet()) {
        newKids.add(new MyProfileNode(key, this).sync());
      }
      newKids.add(new MyProfileNode(DEFAULT_PROFILE, this).sync());
      children = newKids;
      ((DefaultTreeModel)myTree.getModel()).reload();
      expand(myTree);
      return this;
    }
  }

  private interface SyncWithMap {
    SyncWithMap sync();
  }

  private class MyProfileNode extends DefaultMutableTreeNode implements SyncWithMap {
    private final String myKey;

    public MyProfileNode(String key, MyRootNode parent) {
      super(key);
      setParent(parent);
      myKey = key;
    }

    @Override
    public SyncWithMap sync() {
      final List<Module> nodeModules;
      if (DEFAULT_PROFILE.equals(myKey)) {
        final Module[] allModules = ModuleManager.getInstance(myProject).getSortedModules();
        nodeModules = new ArrayList<Module>(Arrays.asList(allModules));
        for (List<Module> modules : profiles.values()) {
          for (Module module : modules) {
            nodeModules.remove(module);
          }
        }
      } else {
        nodeModules = profiles.get(myKey);
      }
      final Vector vector = new Vector();
      for (Module module : nodeModules) {
        vector.add(new MyModuleNode(module, this));
      }
      children = vector;
      return this;
    }
  }

  private class MyModuleNode extends DefaultMutableTreeNode {
    public MyModuleNode(Module module, MyProfileNode parent) {
      super(module);
      setParent(parent);
      setAllowsChildren(false);
    }
  }

  private class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MyProfileNode) {
        append(((MyProfileNode)value).myKey);
      } else if (value instanceof MyModuleNode) {
        final Module module = (Module)((MyModuleNode)value).getUserObject();
        setIcon(IconLoader.getIcon("/nodes/ModuleClosed.png"));
        append(module.getName());
      }
    }
  }
}
