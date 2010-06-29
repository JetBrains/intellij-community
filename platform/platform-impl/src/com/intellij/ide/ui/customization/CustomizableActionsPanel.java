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
package com.intellij.ide.ui.customization;

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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ImageLoader;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizableActionsPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.customization.CustomizableActionsPanel");

  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);

  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");

  private JButton myEditIconButton;
  private JButton myRemoveActionButton;
  private JButton myAddActionButton;
  private JButton myMoveActionDownButton;
  private JButton myMoveActionUpButton;
  private JPanel myPanel;
  private JTree myActionsTree;
  private JButton myAddSeparatorButton;

  private final TreeExpansionMonitor myTreeExpansionMonitor;

  private CustomActionsSchema mySelectedSchema;



  private JPanel myDetailsPanel;
  private JButton myRestoreDefaultButton;
  public static final Icon FULLISH_ICON = IconLoader.getIcon("/toolbar/unknown.png");
  private DetailsComponent myDetailsComponent;

  public CustomizableActionsPanel() {


    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);

    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myActionsTree);
    myActionsTree.setCellRenderer(new MyTreeCellRenderer());

    setButtonsDisabled();
    final ActionManager actionManager = ActionManager.getInstance();
    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
        final boolean isSingleSelection = selectionPaths != null && selectionPaths.length == 1;
        myAddActionButton.setEnabled(isSingleSelection);
        if (isSingleSelection) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPaths[0].getLastPathComponent();
          String actionId = getActionId(node);
          if (actionId != null) {
            final AnAction action = actionManager.getAction(actionId);
            myEditIconButton.setEnabled(action != null &&
                                        (!action.isDefaultIcon() ||
                                         (action.getTemplatePresentation() != null && action.getTemplatePresentation().getIcon() == null)));
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
        if (selectionPaths != null) {
          for (TreePath selectionPath : selectionPaths) {
            if (selectionPath.getPath() != null && selectionPath.getPath().length <= 2) {
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
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final FindAvailableActionsDialog dlg = new FindAvailableActionsDialog();
          dlg.show();
          if (dlg.isOK()) {
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
      public void actionPerformed(ActionEvent e) {
        myRestoreDefaultButton.setEnabled(true);
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          EditIconDialog dlg = new EditIconDialog((DefaultMutableTreeNode)selectionPath.getLastPathComponent());
          dlg.show();
          if (dlg.isOK()) {
            myActionsTree.repaint();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
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

    myRestoreDefaultButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySelectedSchema.copyFrom(new CustomActionsSchema());
        patchActionsTreeCorrespondingToSchema(root);
        myRestoreDefaultButton.setEnabled(false);
      }
    });

    patchActionsTreeCorrespondingToSchema(root);

    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree);
  }

  private void addCustomizedAction(ActionUrl url) {
    mySelectedSchema.addAction(url);
    myRestoreDefaultButton.setEnabled(true);
  }

  public void initUi() {
    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myDetailsPanel);
  }

  public DetailsComponent getDetails() {
    return myDetailsComponent;
  }

  private void editToolbarIcon(String actionId, DefaultMutableTreeNode node) {
    final AnAction anAction = ActionManager.getInstance().getAction(actionId);
    if (isToolbarAction(node) &&
        anAction.getTemplatePresentation() != null &&
        anAction.getTemplatePresentation().getIcon() == null) {
      final int exitCode = Messages.showOkCancelDialog(IdeBundle.message("error.adding.action.without.icon.to.toolbar"),
                                                       IdeBundle.message("title.unable.to.add.action.without.icon.to.toolbar"),
                                                       Messages.getInformationIcon());
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        mySelectedSchema.addIconCustomization(actionId, null);
        anAction.getTemplatePresentation().setIcon(FULLISH_ICON);
        anAction.setDefaultIcon(false);
        node.setUserObject(Pair.create(actionId, FULLISH_ICON));
        myActionsTree.repaint();
        setCustomizationSchemaForCurrentProjects();
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

  private static void setCustomizationSchemaForCurrentProjects() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
      if (frame != null) {
        frame.updateToolbar();
        frame.updateMenuBar();
      }

      //final FavoritesManager favoritesView = FavoritesManager.getInstance(project);
      //final String[] availableFavoritesLists = favoritesView.getAvailableFavoritesLists();
      //for (String favoritesList : availableFavoritesLists) {
      //  favoritesView.getFavoritesTreeViewPanel(favoritesList).updateTreePopupHandler();
      //}
    }
    final IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null) {
      frame.updateToolbar();
      frame.updateMenuBar();
    }
  }

  public void apply() throws ConfigurationException {
    final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
    if (mySelectedSchema != null) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    restorePathsAfterTreeOptimization(treePaths);
    CustomActionsSchema.getInstance().copyFrom(mySelectedSchema);
    setCustomizationSchemaForCurrentProjects();
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
    myRestoreDefaultButton.setEnabled(mySelectedSchema.isModified(new CustomActionsSchema()));
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

  private static class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        Icon icon = null;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          String name = group.getName();
          setText(name != null ? name : group.getId());
          icon = expanded ? group.getOpenIcon() : group.getIcon();
          if (icon == null) {
            icon = expanded ? getOpenIcon() : getClosedIcon();
          }
        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          setText(action != null ? action.getTemplatePresentation().getText() : actionId);
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
          setText(((QuickList)userObject).getDisplayName());
          icon = QUICK_LIST_ICON;
        }
        else {
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

  protected void doSetIcon(DefaultMutableTreeNode node, String path) {
    String actionId = getActionId(node);
    if (actionId == null) return;

    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null && action.getTemplatePresentation() != null) {
      if (path != null && path.length() > 0) {
        Image image = null;
        try {
          image = ImageLoader.loadFromStream(VfsUtil.convertToURL(VfsUtil.pathToUrl(path.replace(File.separatorChar,
                                                                                                        '/'))).openStream());
        }
        catch (IOException e) {
          LOG.debug(e);
        }
        Icon icon = new File(path).exists() ? IconLoader.getIcon(image) : null;
        if (icon != null) {
          if (icon.getIconWidth() >  EMPTY_ICON.getIconWidth() || icon.getIconHeight() > EMPTY_ICON.getIconHeight()) {
            Messages.showErrorDialog(myActionsTree, IdeBundle.message("custom.icon.validation.message"), IdeBundle.message("custom.icon.validation.title"));
            return;
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
    }
  }

  private static TextFieldWithBrowseButton createBrowseField(){
    TextFieldWithBrowseButton textField = new TextFieldWithBrowseButton();
    textField.setPreferredSize(new Dimension(150, textField.getPreferredSize().height));
    textField.setMinimumSize(new Dimension(150, textField.getPreferredSize().height));
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
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
      setTitle(IdeBundle.message("title.choose.action.icon.path"));
      init();
      myNode = node;
      final String actionId = getActionId(node);
      if (actionId != null) {
        myTextField.setText(mySelectedSchema.getIconPath(actionId));
      }
    }

    protected JComponent createCenterPanel() {
      myTextField = createBrowseField();
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.NORTH);
      return northPanel;
    }

    protected void doOKAction() {
      if (myNode != null) {
        if (myTextField.getText().length() > 0 && !new File(myTextField.getText()).exists()){
          Messages.showErrorDialog(myPanel, IdeBundle.message("error.file.not.found.message", myTextField.getText()));
          return;
        }
        doSetIcon(myNode, myTextField.getText());
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
      setCustomizationSchemaForCurrentProjects();
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

    protected JComponent createCenterPanel() {
      Group rootGroup = ActionsTreeUtil.createMainGroup(null, null, QuickListsManager.getInstance().getAllQuickLists());
      DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
      DefaultTreeModel model = new DefaultTreeModel(root);
      myTree = new Tree();
      myTree.setModel(model);
      myTree.setCellRenderer(new MyTreeCellRenderer());
      final ActionManager actionManager = ActionManager.getInstance();

      mySetIconButton = new JButton(IdeBundle.message("button.set.icon"));
      mySetIconButton.setEnabled(false);
      mySetIconButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            doSetIcon((DefaultMutableTreeNode)selectionPath.getLastPathComponent(), myTextField.getText());
            myTree.repaint();
          }
        }
      });
      myTextField = createBrowseField();
      myTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          enableSetIconButton(actionManager);
        }
      });
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.CENTER);
      northPanel.add(new JLabel(IdeBundle.message("label.icon.path")), BorderLayout.WEST);
      northPanel.add(mySetIconButton, BorderLayout.EAST);
      northPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(northPanel, BorderLayout.NORTH);

      panel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
      myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          enableSetIconButton(actionManager);
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
            final String actionId = getActionId(node);
            if (actionId != null) {
              myTextField.setText(mySelectedSchema.getIconPath(actionId));
            }
          }
        }
      });
      return panel;
    }

    protected void doOKAction() {
      final ActionManager actionManager = ActionManager.getInstance();
      TreeUtil.traverseDepth((TreeNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
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
      setCustomizationSchemaForCurrentProjects();
    }

    protected void enableSetIconButton(ActionManager actionManager) {
      final TreePath selectionPath = myTree.getSelectionPath();
      Object userObject = null;
      if (selectionPath != null) {
        userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
        if (userObject instanceof String) {
          final AnAction action = actionManager.getAction((String)userObject);
          if (action != null &&
              action.getTemplatePresentation() != null &&
              action.getTemplatePresentation().getIcon() != null) {
            mySetIconButton.setEnabled(!action.isDefaultIcon());
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

      Set<Object> actions = new HashSet<Object>();
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

    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog";
    }
  }
}
