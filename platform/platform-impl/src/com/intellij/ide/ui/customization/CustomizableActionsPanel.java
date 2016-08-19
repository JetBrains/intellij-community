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
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.impl.ui.ActionsTree;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ImageLoader;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizableActionsPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.customization.CustomizableActionsPanel");

  private JButton myEditIconButton;
  private JButton myRemoveActionButton;
  private JButton myAddActionButton;
  private JButton myMoveActionDownButton;
  private JButton myMoveActionUpButton;
  private JPanel myPanel;
  private JTree myActionsTree;
  private JButton myAddSeparatorButton;

  private CustomActionsSchema mySelectedSchema;

  private JButton myRestoreAllDefaultButton;
  private JButton myRestoreDefaultButton;

  public CustomizableActionsPanel() {
    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);
    TreeUIHelper.getInstance().installTreeSpeedSearch(myActionsTree, new TreePathStringConvertor(), true);

    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myActionsTree);
    myActionsTree.setCellRenderer(new MyTreeCellRenderer());

    setButtonsDisabled();
    final ActionManager actionManager = ActionManager.getInstance();
    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
        final boolean isSingleSelection = selectionPaths != null && selectionPaths.length == 1;
        myAddActionButton.setEnabled(isSingleSelection);
        if (isSingleSelection) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPaths[0].getLastPathComponent();
          String actionId = getActionId(node);
          if (actionId != null) {
            final AnAction action = actionManager.getAction(actionId);
            myEditIconButton.setEnabled(action != null);
          }
          else {
            myEditIconButton.setEnabled(false);
          }
        }
        else {
          myEditIconButton.setEnabled(false);
        }
        myAddSeparatorButton.setEnabled(isSingleSelection);
        myRemoveActionButton.setEnabled(selectionPaths != null);
        myRestoreDefaultButton.setEnabled(!findActionsUnderSelection().isEmpty());
        if (selectionPaths != null) {
          for (TreePath selectionPath : selectionPaths) {
            if (selectionPath.getPath().length <= 2) {
              setButtonsDisabled();
              return;
            }
          }
        }
        myMoveActionUpButton.setEnabled(isMoveSupported(myActionsTree, -1));
        myMoveActionDownButton.setEnabled(isMoveSupported(myActionsTree, 1));
      }
    });

    myAddActionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final FindAvailableActionsDialog dlg = new FindAvailableActionsDialog();
          if (dlg.showAndGet()) {
            final Set<Object> toAdd = dlg.getTreeSelectedActionIds();
            if (toAdd == null) return;
            for (final Object o : toAdd) {
              final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(new TreePath(node.getPath())), o, ActionUrl.ADDED,
                                                  node.getParent().getIndex(node) + 1);
              addCustomizedAction(url);
              ActionUrl.changePathInActionsTree(myActionsTree, url);
              if (o instanceof String) {
                DefaultMutableTreeNode current = new DefaultMutableTreeNode(url.getComponent());
                current.setParent((DefaultMutableTreeNode)node.getParent());
                editToolbarIcon((String)o, current);
              }
            }
            ((DefaultTreeModel)myActionsTree.getModel()).reload();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myEditIconButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRestoreAllDefaultButton.setEnabled(true);
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          EditIconDialog dlg = new EditIconDialog((DefaultMutableTreeNode)selectionPath.getLastPathComponent());
          if (dlg.showAndGet()) {
            myActionsTree.repaint();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(selectionPath), Separator.getInstance(), ActionUrl.ADDED,
                                              node.getParent().getIndex(node) + 1);
          ActionUrl.changePathInActionsTree(myActionsTree, url);
          addCustomizedAction(url);
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });


    myRemoveActionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (TreePath treePath : selectionPath) {
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.DELETED);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            addCustomizedAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myMoveActionUpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (TreePath treePath : selectionPath) {
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition - 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            addCustomizedAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
          TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
          for (TreePath path : selectionPath) {
            myActionsTree.addSelectionPath(path);
          }
        }
      }
    });

    myMoveActionDownButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (int i = selectionPath.length - 1; i >= 0; i--) {
            TreePath treePath = selectionPath[i];
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition + 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            addCustomizedAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
          TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
          for (TreePath path : selectionPath) {
            myActionsTree.addSelectionPath(path);
          }
        }
      }
    });

    myRestoreAllDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySelectedSchema.copyFrom(new CustomActionsSchema());
        patchActionsTreeCorrespondingToSchema(root);
        myRestoreAllDefaultButton.setEnabled(false);
      }
    });

    myRestoreDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<ActionUrl> otherActions = new ArrayList<>(mySelectedSchema.getActions());
        otherActions.removeAll(findActionsUnderSelection());
        mySelectedSchema.copyFrom(new CustomActionsSchema());
        for (ActionUrl otherAction : otherActions) {
          mySelectedSchema.addAction(otherAction);
        }
        final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
        patchActionsTreeCorrespondingToSchema(root);
        restorePathsAfterTreeOptimization(treePaths);
        myRestoreDefaultButton.setEnabled(false);
      }
    });

    patchActionsTreeCorrespondingToSchema(root);

    TreeExpansionMonitor.install(myActionsTree);
  }

  private List<ActionUrl> findActionsUnderSelection() {
    final ArrayList<ActionUrl> actions = new ArrayList<>();
    final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath path : selectionPaths) {
        final ActionUrl selectedUrl = CustomizationUtil.getActionUrl(path, ActionUrl.MOVE);
        final ArrayList<String> selectedGroupPath = new ArrayList<>(selectedUrl.getGroupPath());
        final Object component = selectedUrl.getComponent();
        if (component instanceof Group) {
          selectedGroupPath.add(((Group)component).getName());
          for (ActionUrl action : mySelectedSchema.getActions()) {
            final ArrayList<String> groupPath = action.getGroupPath();
            final int idx = Collections.indexOfSubList(groupPath, selectedGroupPath);
            if (idx > -1) {
              actions.add(action);
            }
          }
        }
      }
    }
    return actions;
  }

  private void addCustomizedAction(ActionUrl url) {
    mySelectedSchema.addAction(url);
    myRestoreAllDefaultButton.setEnabled(true);
  }

  private void editToolbarIcon(String actionId, DefaultMutableTreeNode node) {
    final AnAction anAction = ActionManager.getInstance().getAction(actionId);
    if (isToolbarAction(node) && anAction.getTemplatePresentation().getIcon() == null) {
      final int exitCode = Messages.showOkCancelDialog(IdeBundle.message("error.adding.action.without.icon.to.toolbar"),
                                                       IdeBundle.message("title.unable.to.add.action.without.icon.to.toolbar"),
                                                       Messages.getInformationIcon());
      if (exitCode == Messages.OK) {
        mySelectedSchema.addIconCustomization(actionId, null);
        anAction.getTemplatePresentation().setIcon(AllIcons.Toolbar.Unknown);
        anAction.getTemplatePresentation().setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.Toolbar.Unknown));
        anAction.setDefaultIcon(false);
        node.setUserObject(Pair.create(actionId, AllIcons.Toolbar.Unknown));
        myActionsTree.repaint();
        CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
      }
    }
  }

  private void setButtonsDisabled() {
    myRemoveActionButton.setEnabled(false);
    myAddActionButton.setEnabled(false);
    myEditIconButton.setEnabled(false);
    myAddSeparatorButton.setEnabled(false);
    myMoveActionDownButton.setEnabled(false);
    myMoveActionUpButton.setEnabled(false);
  }

  private static boolean isMoveSupported(JTree tree, int dir) {
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      DefaultMutableTreeNode parent = null;
      for (TreePath treePath : selectionPaths)
        if (treePath.getLastPathComponent() != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          if (parent == null) {
            parent = (DefaultMutableTreeNode)node.getParent();
          }
          if (parent != node.getParent()) {
            return false;
          }
          if (dir > 0) {
            if (parent.getIndex(node) == parent.getChildCount() - 1) {
              return false;
            }
          }
          else {
            if (parent.getIndex(node) == 0) {
              return false;
            }
          }
        }
      return true;
    }
    return false;
  }


  public JPanel getPanel() {
    return myPanel;
  }

  public void apply() throws ConfigurationException {
    final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
    if (mySelectedSchema != null) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    restorePathsAfterTreeOptimization(treePaths);
    CustomActionsSchema.getInstance().copyFrom(mySelectedSchema);
    CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
  }

  private void restorePathsAfterTreeOptimization(final List<TreePath> treePaths) {
    for (final TreePath treePath : treePaths) {
      myActionsTree.expandPath(CustomizationUtil.getPathByUserObjects(myActionsTree, treePath));
    }
  }

  public void reset() {
    mySelectedSchema = new CustomActionsSchema();
    mySelectedSchema.copyFrom(CustomActionsSchema.getInstance());
    patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    myRestoreAllDefaultButton.setEnabled(mySelectedSchema.isModified(new CustomActionsSchema()));
    myActionsTree.setSelectionRow(0);
  }

  public boolean isModified() {
    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    return CustomActionsSchema.getInstance().isModified(mySelectedSchema);
  }

  private void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillActionGroups(root);
      for (final ActionUrl actionUrl : mySelectedSchema.getActions()) {
        ActionUrl.changePathInActionsTree(myActionsTree, actionUrl);
      }
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private static class TreePathStringConvertor implements Convertor<TreePath, String> {
    @Override
    public String convert(TreePath o) {
      Object node = o.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        Object object = ((DefaultMutableTreeNode)node).getUserObject();
        if (object instanceof Group) return ((Group)object).getName();
        if (object instanceof QuickList) return ((QuickList)object).getName();
        String actionId;
        if (object instanceof String) {
          actionId = (String)object;
        }
        else if (object instanceof Pair) {
          actionId = (String)((Pair)object).first;
        }
        else {
          return "";
        }
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action != null) {
          return action.getTemplatePresentation().getText();
        }
      }
      return "";
    }
  }

  private static class MyTreeCellRenderer extends JBDefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, false);
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        Icon icon = null;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          String name = group.getName();
          setText(name != null ? name : group.getId());
          icon = ObjectUtils.notNull(group.getIcon(), AllIcons.Nodes.Folder);
        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          String name = action != null ? action.getTemplatePresentation().getText() : null;
          setText(!StringUtil.isEmptyOrSpaces(name) ? name : actionId);
          if (action != null) {
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
          }
        }
        else if (userObject instanceof Pair) {
          String actionId = (String)((Pair)userObject).first;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          setText(action != null ? action.getTemplatePresentation().getText() : actionId);
          icon = (Icon)((Pair)userObject).second;
        }
        else if (userObject instanceof Separator) {
          setText("-------------");
        }
        else if (userObject instanceof QuickList) {
          setText(((QuickList)userObject).getName());
          icon = AllIcons.Actions.QuickList;
        }
        else if (userObject != null) {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        setIcon(ActionsTree.getEvenIcon(icon));

        if (sel) {
          setForeground(UIUtil.getTreeSelectionForeground());
        }
        else {
          setForeground(UIUtil.getTreeForeground());
        }
      }
      return this;
    }
  }

  private static boolean isToolbarAction(DefaultMutableTreeNode node) {
    return node.getParent() != null && ((DefaultMutableTreeNode)node.getParent()).getUserObject() instanceof Group &&
           ((Group)((DefaultMutableTreeNode)node.getParent()).getUserObject()).getName().equals(ActionsTreeUtil.MAIN_TOOLBAR);
  }

  @Nullable
  private static String getActionId(DefaultMutableTreeNode node) {
    return (String)(node.getUserObject() instanceof String ? node.getUserObject() :
                    node.getUserObject() instanceof Pair ? ((Pair)node.getUserObject()).first : null);
  }

  protected boolean doSetIcon(DefaultMutableTreeNode node, @Nullable String path, Component component) {
    if (StringUtil.isNotEmpty(path) && !new File(path).isFile()) {
      Messages
        .showErrorDialog(component, IdeBundle.message("error.file.not.found.message", path), IdeBundle.message("title.choose.action.icon"));
      return false;
    }

    String actionId = getActionId(node);
    if (actionId == null) return false;

    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) {
      if (StringUtil.isNotEmpty(path)) {
        Image image = null;
        try {
          image = ImageLoader.loadFromStream(VfsUtilCore.convertToURL(VfsUtil.pathToUrl(path.replace(File.separatorChar,
                                                                                                     '/'))).openStream());
        }
        catch (IOException e) {
          LOG.debug(e);
        }
        Icon icon = new File(path).exists() ? IconLoader.getIcon(image) : null;
        if (icon != null) {
          if (icon.getIconWidth() >  EmptyIcon.ICON_18.getIconWidth() || icon.getIconHeight() > EmptyIcon.ICON_18.getIconHeight()) {
            Messages.showErrorDialog(component, IdeBundle.message("custom.icon.validation.message"), IdeBundle.message("title.choose.action.icon"));
            return false;
          }
          node.setUserObject(Pair.create(actionId, icon));
          mySelectedSchema.addIconCustomization(actionId, path);
        }
      }
      else {
        node.setUserObject(Pair.create(actionId, null));
        mySelectedSchema.removeIconCustomization(actionId);
        final DefaultMutableTreeNode nodeOnToolbar = findNodeOnToolbar(actionId);
        if (nodeOnToolbar != null){
          editToolbarIcon(actionId, nodeOnToolbar);
          node.setUserObject(nodeOnToolbar.getUserObject());
        }
      }
      return true;
    }
    return false;
  }

  private static TextFieldWithBrowseButton createBrowseField(){
    TextFieldWithBrowseButton textField = new TextFieldWithBrowseButton();
    textField.setPreferredSize(new Dimension(200, textField.getPreferredSize().height));
    textField.setMinimumSize(new Dimension(200, textField.getPreferredSize().height));
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        //noinspection HardCodedStringLiteral
        return file.getName().endsWith(".png");
      }
    };
    textField.addBrowseFolderListener(IdeBundle.message("title.browse.icon"), IdeBundle.message("prompt.browse.icon.for.selected.action"), null,
                                      fileChooserDescriptor);
    InsertPathAction.addTo(textField.getTextField(), fileChooserDescriptor);
    return textField;
  }

  private class EditIconDialog extends DialogWrapper {
    private final DefaultMutableTreeNode myNode;
    protected TextFieldWithBrowseButton myTextField;

    protected EditIconDialog(DefaultMutableTreeNode node) {
      super(false);
      setTitle(IdeBundle.message("title.choose.action.icon"));
      init();
      myNode = node;
      final String actionId = getActionId(node);
      if (actionId != null) {
        final String iconPath = mySelectedSchema.getIconPath(actionId);
        myTextField.setText(FileUtil.toSystemDependentName(iconPath));
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTextField.getChildComponent();
    }

    @Override
    protected String getDimensionServiceKey() {
      return getClass().getName();
    }

    @Override
    protected JComponent createCenterPanel() {
      myTextField = createBrowseField();
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.NORTH);
      return northPanel;
    }

    @Override
    protected void doOKAction() {
      if (myNode != null) {
        if (!doSetIcon(myNode, myTextField.getText(), getContentPane())) {
          return;
        }
        final Object userObject = myNode.getUserObject();
        if (userObject instanceof Pair) {
          String actionId = (String)((Pair)userObject).first;
          final AnAction action = ActionManager.getInstance().getAction(actionId);
          final Icon icon = (Icon)((Pair)userObject).second;
          action.getTemplatePresentation().setIcon(icon);
          action.setDefaultIcon(icon == null);
          editToolbarIcon(actionId, myNode);
        }
        myActionsTree.repaint();
      }
      CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
      super.doOKAction();
    }
  }

  @Nullable
  private DefaultMutableTreeNode findNodeOnToolbar(String actionId){
    final TreeNode toolbar = ((DefaultMutableTreeNode)myActionsTree.getModel().getRoot()).getChildAt(1);
    for(int i = 0; i < toolbar.getChildCount(); i++){
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)toolbar.getChildAt(i);
      final String childId = getActionId(child);
      if (childId != null && childId.equals(actionId)){
        return child;
      }
    }
    return null;
  }

  private class FindAvailableActionsDialog extends DialogWrapper{
    private JTree myTree;
    private JButton mySetIconButton;
    private TextFieldWithBrowseButton myTextField;

    FindAvailableActionsDialog() {
      super(false);
      setTitle(IdeBundle.message("action.choose.actions.to.add"));
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      Group rootGroup = ActionsTreeUtil.createMainGroup(null, null, QuickListsManager.getInstance().getAllQuickLists());
      DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
      DefaultTreeModel model = new DefaultTreeModel(root);
      myTree = new Tree();
      TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, new TreePathStringConvertor(), true);
      myTree.setModel(model);
      myTree.setCellRenderer(new MyTreeCellRenderer());
      final ActionManager actionManager = ActionManager.getInstance();

      mySetIconButton = new JButton(IdeBundle.message("button.set.icon"));
      mySetIconButton.setEnabled(false);
      mySetIconButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            doSetIcon((DefaultMutableTreeNode)selectionPath.getLastPathComponent(), myTextField.getText(), getContentPane());
            myTree.repaint();
          }
        }
      });
      myTextField = createBrowseField();
      myTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          enableSetIconButton(actionManager);
        }
      });
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.CENTER);
      final JLabel label = new JLabel(IdeBundle.message("label.icon.path"));
      label.setLabelFor(myTextField.getChildComponent());
      northPanel.add(label, BorderLayout.WEST);
      northPanel.add(mySetIconButton, BorderLayout.EAST);
      northPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(northPanel, BorderLayout.NORTH);

      panel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
      myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
          enableSetIconButton(actionManager);
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
            final String actionId = getActionId(node);
            if (actionId != null) {
              final String iconPath = mySelectedSchema.getIconPath(actionId);
              myTextField.setText(FileUtil.toSystemDependentName(iconPath));
            }
          }
        }
      });
      return panel;
    }

    @Override
    protected void doOKAction() {
      final ActionManager actionManager = ActionManager.getInstance();
      TreeUtil.traverseDepth((TreeNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        @Override
        public boolean accept(Object node) {
          if (node instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode)node;
            final Object userObject = mutableNode.getUserObject();
            if (userObject instanceof Pair) {
              String actionId = (String)((Pair)userObject).first;
              final AnAction action = actionManager.getAction(actionId);
              Icon icon = (Icon)((Pair)userObject).second;
              action.getTemplatePresentation().setIcon(icon);
              action.setDefaultIcon(icon == null);
              editToolbarIcon(actionId, mutableNode);
            }
          }
          return true;
        }
      });
      super.doOKAction();
      CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
    }

    protected void enableSetIconButton(ActionManager actionManager) {
      final TreePath selectionPath = myTree.getSelectionPath();
      Object userObject = null;
      if (selectionPath != null) {
        userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
        if (userObject instanceof String) {
          final AnAction action = actionManager.getAction((String)userObject);
          if (action != null && action.getTemplatePresentation().getIcon() != null) {
            mySetIconButton.setEnabled(true);
            return;
          }
        }
      }
      mySetIconButton.setEnabled(myTextField.getText().length() != 0 &&
                                 selectionPath != null &&
                                 new DefaultMutableTreeNode(selectionPath).isLeaf() &&
                                 !(userObject instanceof Separator));
    }

    @Nullable
    public Set<Object> getTreeSelectedActionIds() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return null;

      Set<Object> actions = new HashSet<>();
      for (TreePath path : paths) {
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
          Object userObject = defNode.getUserObject();
          actions.add(userObject);
        }
      }
      return actions;
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog";
    }
  }
}
