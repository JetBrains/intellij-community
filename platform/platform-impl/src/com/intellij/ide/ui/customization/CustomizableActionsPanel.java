// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

public class CustomizableActionsPanel {
  private static final Logger LOG = Logger.getInstance(CustomizableActionsPanel.class);

  private JPanel myPanel;
  protected JTree myActionsTree;
  private JPanel myTopPanel;
  protected CustomActionsSchema mySelectedSchema;

  public CustomizableActionsPanel() {
    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree = new Tree(model);
    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    myActionsTree.setCellRenderer(createDefaultRenderer());

    patchActionsTreeCorrespondingToSchema(root);

    TreeExpansionMonitor.install(myActionsTree);
    myTopPanel = new BorderLayoutPanel();
    myTopPanel.add(setupFilterComponent(myActionsTree), BorderLayout.WEST);
    myTopPanel.add(createToolbar(), BorderLayout.CENTER);

    myPanel = new BorderLayoutPanel(5, 5);
    myPanel.add(myTopPanel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myActionsTree), BorderLayout.CENTER);
  }

  private ActionToolbarImpl createToolbar() {
    ActionGroup addGroup = new DefaultActionGroup(new AddActionActionTreeSelectionAction()/*, new AddGroupAction()*/, new AddSeparatorAction());
    addGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.add.action.group"));
    addGroup.getTemplatePresentation().setIcon(AllIcons.General.Add);
    addGroup.setPopup(true);
    ActionGroup restoreGroup = getRestoreGroup();
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(addGroup, new RemoveAction(), new EditIconAction(), new MoveUpAction(), new MoveDownAction(), restoreGroup), true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    toolbar.setTargetComponent(myTopPanel);
    return toolbar;
  }

  @NotNull
  protected ActionGroup getRestoreGroup() {
    ActionGroup restoreGroup = new DefaultActionGroup(new RestoreSelectionAction(), new RestoreAllAction());
    restoreGroup.setPopup(true);
    restoreGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.restore.action.group"));
    restoreGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
    return restoreGroup;
  }

  static FilterComponent setupFilterComponent(JTree tree) {
    final TreeSpeedSearch mySpeedSearch = new TreeSpeedSearch(tree, new TreePathStringConvertor(), true) {
      @Override
      public boolean isPopupActive() {
        return /*super.isPopupActive()*/true;
      }

      @Override
      public void showPopup(String searchText) {
        //super.showPopup(searchText);
      }

      @Override
      protected boolean isSpeedSearchEnabled() {
        return /*super.isSpeedSearchEnabled()*/false;
      }

      @Override
      public void showPopup() {
        //super.showPopup();
      }
    };
    final FilterComponent filterComponent = new FilterComponent("CUSTOMIZE_ACTIONS", 5) {
      @Override
      public void filter() {
        mySpeedSearch.findAndSelectElement(getFilter());
        mySpeedSearch.getComponent().repaint();
      }
    };
    JTextField textField = filterComponent.getTextEditor();
    int[] keyCodes = {KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_UP, KeyEvent.VK_DOWN};
    for (int keyCode : keyCodes) {
      new DumbAwareAction(){
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String filter = filterComponent.getFilter();
          if (!StringUtil.isEmpty(filter)) {
            mySpeedSearch.adjustSelection(keyCode, filter);
          }
        }
      }.registerCustomShortcutSet(keyCode, 0, textField);

    }
    return filterComponent;
  }

  private void addCustomizedAction(ActionUrl url) {
    mySelectedSchema.addAction(url);
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
    if (SystemInfo.isMac) {
      TouchbarSupport.reloadAllActions();
    }

    CustomActionsListener.fireSchemaChanged();
  }

  private void restorePathsAfterTreeOptimization(final List<? extends TreePath> treePaths) {
    for (final TreePath treePath : treePaths) {
      myActionsTree.expandPath(CustomizationUtil.getPathByUserObjects(myActionsTree, treePath));
    }
  }

  public void reset() {
    mySelectedSchema = new CustomActionsSchema();
    mySelectedSchema.copyFrom(CustomActionsSchema.getInstance());
    patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    myActionsTree.setSelectionRow(0);
  }

  public boolean isModified() {
    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    return CustomActionsSchema.getInstance().isModified(mySelectedSchema);
  }

  protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillCorrectedActionGroups(root);
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
          actionId = (String)((Pair<?, ?>)object).first;
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

  static TreeCellRenderer createDefaultRenderer() {
    return new MyTreeCellRenderer();
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        CustomizationUtil.acceptObjectIconAndText(userObject, (text, icon) -> {
          append(text);
          setIcon(icon);
        });
        setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      }
    }
  }

  @Nullable
  private static String getActionId(DefaultMutableTreeNode node) {
    return (String)(node.getUserObject() instanceof String ? node.getUserObject() :
                    node.getUserObject() instanceof Pair ? ((Pair<?, ?>)node.getUserObject()).first : null);
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
          image = ImageLoader.loadCustomIcon(new File(path.replace(File.separatorChar,'/')));
        }
        catch (IOException e) {
          LOG.debug(e);
        }
        Icon icon = image != null ? new JBImageIcon(image) : null;
        if (icon != null) {
          node.setUserObject(Pair.create(actionId, icon));
          mySelectedSchema.addIconCustomization(actionId, path);
        }
      }
      else {
        node.setUserObject(Pair.create(actionId, null));
        mySelectedSchema.removeIconCustomization(actionId);
        final DefaultMutableTreeNode nodeOnToolbar = findNodeOnToolbar(actionId);
        if (nodeOnToolbar != null){
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
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return file != null && (file.getName().endsWith(".png") || file.getName().endsWith(".svg"));
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
          String actionId = (String)((Pair<?, ?>)userObject).first;
          final AnAction action = ActionManager.getInstance().getAction(actionId);
          final Icon icon = (Icon)((Pair<?, ?>)userObject).second;
          action.getTemplatePresentation().setIcon(icon);
          action.setDefaultIcon(icon == null);
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
    private FilterComponent myFilterComponent;

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
      myTree.setRootVisible(false);
      myTree.setCellRenderer(createDefaultRenderer());
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
        protected void textChanged(@NotNull DocumentEvent e) {
          enableSetIconButton(actionManager);
        }
      });
      JPanel southPanel = new JPanel(new BorderLayout());
      southPanel.add(myTextField, BorderLayout.CENTER);
      final JLabel label = new JLabel(IdeBundle.message("label.icon.path"));
      label.setLabelFor(myTextField.getChildComponent());
      southPanel.add(label, BorderLayout.WEST);
      southPanel.add(mySetIconButton, BorderLayout.EAST);
      southPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(southPanel, BorderLayout.SOUTH);
      myFilterComponent = setupFilterComponent(myTree);
      panel.add(myFilterComponent, BorderLayout.NORTH);

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
      new DoubleClickListener(){
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent event) {
          doOKAction();
          return true;
        }
      }.installOn(myTree);
      return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myFilterComponent.getTextEditor();
    }

    @Override
    protected void doOKAction() {
      final ActionManager actionManager = ActionManager.getInstance();
      TreeUtil.traverseDepth((TreeNode)myTree.getModel().getRoot(), node -> {
        if (node instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode)node;
          final Object userObject = mutableNode.getUserObject();
          if (userObject instanceof Pair) {
            String actionId = (String)((Pair<?, ?>)userObject).first;
            final AnAction action = actionManager.getAction(actionId);
            Icon icon = (Icon)((Pair<?, ?>)userObject).second;
            action.getTemplatePresentation().setIcon(icon);
            action.setDefaultIcon(icon == null);
          }
        }
        return true;
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


  private abstract class TreeSelectionAction extends DumbAwareAction {
    private TreeSelectionAction(@NotNull Supplier<String> text) {
      super(text);
    }

    private TreeSelectionAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths == null) {
        e.getPresentation().setEnabled(false);
        return;
      }
      for (TreePath path : selectionPaths) {
        if (path.getPath().length <= 2) {
          e.getPresentation().setEnabled(false);
          return;
        }
      }
    }

    protected final boolean isSingleSelection() {
      final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      return selectionPaths != null && selectionPaths.length == 1;
    }
  }

  private final class AddActionActionTreeSelectionAction extends TreeSelectionAction {
    private AddActionActionTreeSelectionAction() {
      super(IdeBundle.messagePointer("button.add.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      int row = myActionsTree.getRowForPath(selectionPath);
      if (selectionPath != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        FindAvailableActionsDialog dlg = new FindAvailableActionsDialog();
        if (dlg.showAndGet()) {
          Set<Object> toAdd = dlg.getTreeSelectedActionIds();
          if (toAdd == null) return;
          for (Object o : toAdd) {
            ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(new TreePath(node.getPath())), o, ActionUrl.ADDED,
                                          node.getParent().getIndex(node) + 1);
            addCustomizedAction(url);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            if (o instanceof String) {
              DefaultMutableTreeNode current = new DefaultMutableTreeNode(url.getComponent());
              current.setParent((DefaultMutableTreeNode)node.getParent());
              selectionPath = selectionPath.getParentPath().pathByAddingChild(url.getComponent());
            }
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
          TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
          myActionsTree.setSelectionRow(row + 1);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }
  }

  private final class AddSeparatorAction extends TreeSelectionAction {
    private AddSeparatorAction() {
      super(IdeBundle.messagePointer("button.add.separator"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
      myActionsTree.setSelectionRow(myActionsTree.getRowForPath(selectionPath) + 1);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }
  }

  private final class RemoveAction extends TreeSelectionAction {
    private RemoveAction() {
      super(IdeBundle.messagePointer("button.remove"), Presentation.NULL_STRING, AllIcons.General.Remove);
      ShortcutSet shortcutSet = KeymapUtil.filterKeyStrokes(CommonShortcuts.getDelete(),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
      if (shortcutSet != null) {
        registerCustomShortcutSet(shortcutSet, myPanel);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
  }

  private final class EditIconAction extends TreeSelectionAction {
    private EditIconAction() {
      super(IdeBundle.messagePointer("button.edit.action.icon"), Presentation.NULL_STRING, AllIcons.Actions.Edit);
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        final ActionManager actionManager = ActionManager.getInstance();
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myActionsTree.getLeadSelectionPath().getLastPathComponent();
        String actionId = getActionId(node);
        if (actionId != null) {
          final AnAction action = actionManager.getAction(actionId);
          e.getPresentation().setEnabled(action != null);
        }
        else {
          e.getPresentation().setEnabled(false);
        }

      }
    }
  }

  private final class MoveUpAction extends TreeSelectionAction {
    private MoveUpAction() {
      super(IdeBundle.messagePointer("button.move.up"), Presentation.NULL_STRING, AllIcons.Actions.MoveUp);
      registerCustomShortcutSet(CommonShortcuts.MOVE_UP, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, -1));
    }
  }

  private final class MoveDownAction extends TreeSelectionAction {
    private MoveDownAction() {
      super(IdeBundle.messagePointer("button.move.down"), Presentation.NULL_STRING, AllIcons.Actions.MoveDown);
      registerCustomShortcutSet(CommonShortcuts.MOVE_DOWN, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, 1));
    }
  }

  private final class RestoreSelectionAction extends DumbAwareAction {
    private RestoreSelectionAction() {
      super(IdeBundle.messagePointer("button.restore.selected.groups"));
    }

    private Pair<TreeSet<String>, List<ActionUrl>> findActionsUnderSelection() {
      ArrayList<ActionUrl> actions = new ArrayList<>();
      TreeSet<String> selectedNames = new TreeSet<>();
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths != null) {
        for (TreePath path : selectionPaths) {
          ActionUrl selectedUrl = CustomizationUtil.getActionUrl(path, ActionUrl.MOVE);
          ArrayList<String> selectedGroupPath = new ArrayList<>(selectedUrl.getGroupPath());
          Object component = selectedUrl.getComponent();
          if (component instanceof Group) {
            selectedGroupPath.add(((Group)component).getName());
            selectedNames.add(((Group)component).getName());
            for (ActionUrl action : mySelectedSchema.getActions()) {
              ArrayList<String> groupPath = action.getGroupPath();
              int idx = Collections.indexOfSubList(groupPath, selectedGroupPath);
              if (idx > -1) {
                actions.add(action);
              }
            }
          }
        }
      }
      return Pair.create(selectedNames, actions);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<ActionUrl> otherActions = new ArrayList<>(mySelectedSchema.getActions());
      otherActions.removeAll(findActionsUnderSelection().second);
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      for (ActionUrl otherAction : otherActions) {
        mySelectedSchema.addAction(otherAction);
      }
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
      restorePathsAfterTreeOptimization(treePaths);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Pair<TreeSet<String>, List<ActionUrl>> selection = findActionsUnderSelection();
      e.getPresentation().setEnabled(!selection.second.isEmpty());
      if (selection.first.size() != 1) {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selected.groups"));
      }
      else {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selection", selection.first.iterator().next()));
      }
    }
  }

  private final class RestoreAllAction extends DumbAwareAction {
    private RestoreAllAction() {
      super(IdeBundle.messagePointer("button.restore.all"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySelectedSchema.isModified(new CustomActionsSchema()));
    }
  }
}
