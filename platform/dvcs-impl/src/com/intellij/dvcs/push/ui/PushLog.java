/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.EditSourceForDialogAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COLLAPSE_ALL;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_ALL;
import static com.intellij.util.containers.ContainerUtil.emptyList;

public class PushLog extends JPanel implements DataProvider {

  private static final String CONTEXT_MENU = "Vcs.Push.ContextMenu";
  private static final String START_EDITING = "startEditing";
  private static final String SPLITTER_PROPORTION = "Vcs.Push.Splitter.Proportion";
  private final ChangesBrowser myChangesBrowser;
  private final CheckboxTree myTree;
  private final MyTreeCellRenderer myTreeCellRenderer;
  private final JScrollPane myScrollPane;
  private final VcsCommitInfoBalloon myBalloon;
  private boolean myShouldRepaint = false;
  private boolean mySyncStrategy;
  @Nullable private String mySyncRenderedText;
  private final boolean myAllowSyncStrategy;

  public PushLog(Project project, final CheckedTreeNode root, final boolean allowSyncStrategy) {
    myAllowSyncStrategy = allowSyncStrategy;
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    treeModel.nodeStructureChanged(root);
    final AnAction quickDocAction = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC);
    myTreeCellRenderer = new MyTreeCellRenderer();
    myTree = new CheckboxTree(myTreeCellRenderer, root) {

      protected boolean shouldShowBusyIconIfNeeded() {
        return true;
      }

      public boolean isPathEditable(TreePath path) {
        return isEditable() && path.getLastPathComponent() instanceof DefaultMutableTreeNode;
      }

      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        if (node instanceof EditableTreeNode) {
          ((EditableTreeNode)node).fireOnSelectionChange(node.isChecked());
        }
      }

      @Override
      public String getToolTipText(MouseEvent event) {
        final TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
          return "";
        }
        Object node = path.getLastPathComponent();
        if (node == null || (!(node instanceof DefaultMutableTreeNode))) {
          return "";
        }
        if (node instanceof TooltipNode) {
          return KeymapUtil.createTooltipText(
            ((TooltipNode)node).getTooltip() +
            "<p style='font-style:italic;color:gray;'>Show commit details", quickDocAction) + "</p>";
        }
        return "";
      }

      @Override
      public boolean stopEditing() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof EditableTreeNode) {
          JComponent editedComponent = (JComponent)node.getUserObject();
          InputVerifier verifier = editedComponent.getInputVerifier();
          if (verifier != null && !verifier.verify(editedComponent)) return false;
        }
        boolean result = super.stopEditing();
        if (myShouldRepaint) {
          refreshNode(root);
        }
        restoreSelection(node);
        return result;
      }

      @Override
      public void cancelEditing() {
        DefaultMutableTreeNode lastSelectedPathComponent = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        super.cancelEditing();
        if (myShouldRepaint) {
          refreshNode(root);
        }
        restoreSelection(lastSelectedPathComponent);
      }

      @Override
      protected void installSpeedSearch() {
        new TreeSpeedSearch(this, path -> {
          Object pathComponent = path.getLastPathComponent();
          if (pathComponent instanceof RepositoryNode) {
            return ((RepositoryNode)pathComponent).getRepositoryName();
          }
          return pathComponent.toString();
        });
      }
    };
    myTree.setUI(new MyTreeUi());
    myTree.setBorder(new EmptyBorder(2, 0, 0, 0));  //additional vertical indent
    myTree.setEditable(true);
    myTree.setHorizontalAutoScrollingEnabled(false);
    myTree.setShowsRootHandles(root.getChildCount() > 1);
    MyTreeCellEditor treeCellEditor = new MyTreeCellEditor();
    myTree.setCellEditor(treeCellEditor);
    treeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof EditableTreeNode) {
          JComponent editedComponent = (JComponent)node.getUserObject();
          InputVerifier verifier = editedComponent.getInputVerifier();
          if (verifier != null && !verifier.verify(editedComponent)) {
            // if invalid and interrupted, then revert
            ((EditableTreeNode)node).fireOnCancel();
          }
          else {
            if (mySyncStrategy) {
              resetEditSync();
              ContainerUtil.process(getChildNodesByType(root, RepositoryNode.class, false), node1 -> {
                node1.fireOnChange();
                return true;
              });
            }
            else {
              ((EditableTreeNode)node).fireOnChange();
            }
          }
        }
        myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, true, false);
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof EditableTreeNode) {
          ((EditableTreeNode)node).fireOnCancel();
        }
        resetEditSync();
        myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, true, false);
      }
    });
    // complete editing when interrupt
    myTree.setInvokesStopCellEditing(true);
    myTree.setRootVisible(false);
    TreeUtil.collapseAll(myTree, 1);
    final VcsBranchEditorListener linkMouseListener = new VcsBranchEditorListener(myTreeCellRenderer);
    linkMouseListener.installOn(myTree);
    myBalloon = new VcsCommitInfoBalloon(myTree);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateChangesView();
        myBalloon.updateCommitDetails();
      }
    });
    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof RepositoryNode && myTree.isEditing()) {
          //need to force repaint foreground  for non-focused editing node
          myTree.getCellEditor().getTreeCellEditorComponent(myTree, node, true, false, false, myTree.getRowForPath(
            TreeUtil.getPathFromRoot(node)));
        }
      }
    });
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    //override default tree behaviour.
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "");
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "");
    MyShowCommitInfoAction showCommitInfoAction = new MyShowCommitInfoAction();
    showCommitInfoAction.registerCustomShortcutSet(quickDocAction.getShortcutSet(), myTree);
    ExpandAllAction expandAllAction = new ExpandAllAction(myTree);
    expandAllAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(ACTION_EXPAND_ALL).getShortcutSet(), myTree);
    CollapseAllAction collapseAll = new CollapseAllAction(myTree);
    collapseAll.registerCustomShortcutSet(ActionManager.getInstance().getAction(ACTION_COLLAPSE_ALL).getShortcutSet(), myTree);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    PopupHandler.installPopupHandler(myTree, VcsLogActionPlaces.POPUP_ACTION_GROUP, CONTEXT_MENU);

    myChangesBrowser =
      new ChangesBrowser(project, null, Collections.emptyList(), null, false, false, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
                         null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), myTree);
    final EditSourceForDialogAction editSourceAction = new EditSourceForDialogAction(myChangesBrowser);
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myChangesBrowser);
    myChangesBrowser.addToolbarAction(editSourceAction);
    myChangesBrowser.setMinimumSize(new Dimension(JBUI.scale(200), myChangesBrowser.getPreferredSize().height));
    setDefaultEmptyText();

    JBSplitter splitter = new JBSplitter(SPLITTER_PROPORTION, 0.7f);
    final JComponent syncStrategyPanel = myAllowSyncStrategy ? createStrategyPanel() : null;
    myScrollPane = new JBScrollPane(myTree) {

      @Override
      public void layout() {
        super.layout();
        if (syncStrategyPanel != null) {
          Rectangle bounds = this.getViewport().getBounds();
          int height = bounds.height - syncStrategyPanel.getPreferredSize().height;
          this.getViewport().setBounds(bounds.x, bounds.y, bounds.width, height);
          syncStrategyPanel.setBounds(bounds.x, bounds.y + height, bounds.width,
                                      syncStrategyPanel.getPreferredSize().height);
        }
      }
    };
    if (syncStrategyPanel != null) {
      myScrollPane.setViewport(new MyTreeViewPort(myTree, syncStrategyPanel.getPreferredSize().height));
    }
    myScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    myScrollPane.setOpaque(false);
    if (syncStrategyPanel != null) {
      myScrollPane.add(syncStrategyPanel);
    }
    splitter.setFirstComponent(myScrollPane);
    splitter.setSecondComponent(myChangesBrowser);

    setLayout(new BorderLayout());
    add(splitter);
    myTree.setMinimumSize(new Dimension(JBUI.scale(400), myTree.getPreferredSize().height));
    myTree.setRowHeight(0);
    myScrollPane.setMinimumSize(new Dimension(myTree.getMinimumSize().width, myScrollPane.getPreferredSize().height));
  }

  public void highlightNodeOrFirst(@Nullable RepositoryNode repositoryNode, boolean shouldScrollTo) {
    TreePath selectionPath = repositoryNode != null ? TreeUtil.getPathFromRoot(repositoryNode) : TreeUtil.getFirstNodePath(myTree);
    myTree.setSelectionPath(selectionPath);
    if (shouldScrollTo) {
      myTree.scrollPathToVisible(selectionPath);
    }
  }

  private class MyShowCommitInfoAction extends DumbAwareAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      myBalloon.showCommitDetails();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedCommitNodes().size() == 1);
    }
  }

  private void restoreSelection(@Nullable DefaultMutableTreeNode node) {
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
    }
  }

  private JComponent createStrategyPanel() {
    final JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBackground(myTree.getBackground());
    final LinkLabel<String> linkLabel = new LinkLabel<>("Edit all targets", null);
    linkLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
    linkLabel.setListener(new LinkListener<String>() {
      @Override
      public void linkSelected(LinkLabel aSource, String aLinkData) {
        if (linkLabel.isEnabled()) {
          startSyncEditing();
        }
      }
    }, null);
    myTree.addPropertyChangeListener(PushLogTreeUtil.EDIT_MODE_PROP, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        Boolean editMode = (Boolean)evt.getNewValue();
        linkLabel.setEnabled(!editMode);
        linkLabel.setPaintUnderline(!editMode);
        linkLabel.repaint();
      }
    });
    labelPanel.add(linkLabel, BorderLayout.EAST);
    return labelPanel;
  }

  private void startSyncEditing() {
    mySyncStrategy = true;
    DefaultMutableTreeNode nodeToEdit = getFirstNodeToEdit();
    if (nodeToEdit != null) {
      myTree.startEditingAtPath(TreeUtil.getPathFromRoot(nodeToEdit));
    }
  }

  @NotNull
  private static List<Change> collectAllChanges(@NotNull List<CommitNode> commitNodes) {
    return CommittedChangesTreeBrowser.zipChanges(collectChanges(commitNodes));
  }

  @NotNull
  private static List<CommitNode> collectSelectedCommitNodes(@NotNull List<DefaultMutableTreeNode> selectedNodes) {
    //addAll Commit nodes from selected Repository nodes;
    List<CommitNode> nodes = StreamEx.of(selectedNodes)
      .select(RepositoryNode.class)
      .toFlatList(node -> getChildNodesByType(node, CommitNode.class, true));
    // add all others selected Commit nodes;
    nodes.addAll(StreamEx.of(selectedNodes)
                   .select(CommitNode.class)
                   .filter(node -> !nodes.contains(node))
                   .toList());
    return nodes;
  }

  @NotNull
  private static List<Change> collectChanges(@NotNull List<CommitNode> commitNodes) {
    List<Change> changes = ContainerUtil.newArrayList();
    for (CommitNode node : commitNodes) {
      changes.addAll(node.getUserObject().getChanges());
    }
    return changes;
  }

  @NotNull
  private static <T> List<T> getChildNodesByType(@NotNull DefaultMutableTreeNode node, Class<T> type, boolean reverseOrder) {
    List<T> nodes = ContainerUtil.newArrayList();
    if (node.getChildCount() < 1) {
      return nodes;
    }
    for (DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node.getFirstChild();
         childNode != null;
         childNode = (DefaultMutableTreeNode)node.getChildAfter(childNode)) {
      if (type.isInstance(childNode)) {
        @SuppressWarnings("unchecked")
        T nodeT = (T)childNode;
        if (reverseOrder) {
          nodes.add(0, nodeT);
        }
        else {
          nodes.add(nodeT);
        }
      }
    }
    return nodes;
  }

  @NotNull
  private static List<Integer> getSortedRows(@NotNull int[] rows) {
    List<Integer> sorted = ContainerUtil.newArrayList();
    for (int row : rows) {
      sorted.add(row);
    }
    Collections.sort(sorted, Collections.reverseOrder());
    return sorted;
  }

  private void updateChangesView() {
    List<CommitNode> commitNodes = getSelectedCommitNodes();
    if (!commitNodes.isEmpty()) {
      myChangesBrowser.getViewer().setEmptyText("No differences");
    }
    else {
      setDefaultEmptyText();
    }
    myChangesBrowser.setChangesToDisplay(collectAllChanges(commitNodes));
  }

  private void setDefaultEmptyText() {
    myChangesBrowser.getViewer().setEmptyText("No commits selected");
  }

  // Make changes available for diff action; revisionNumber for create patch and copy revision number actions
  @Nullable
  @Override
  public Object getData(String id) {
    if (VcsDataKeys.CHANGES.is(id)) {
      List<CommitNode> commitNodes = getSelectedCommitNodes();
      return ArrayUtil.toObjectArray(collectAllChanges(commitNodes), Change.class);
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(id)) {
      List<CommitNode> commitNodes = getSelectedCommitNodes();
      return ArrayUtil.toObjectArray(ContainerUtil.map(commitNodes, commitNode -> {
        Hash hash = commitNode.getUserObject().getId();
        return new TextRevisionNumber(hash.asString(), hash.toShortString());
      }), VcsRevisionNumber.class);
    }
    return null;
  }

  @NotNull
  private List<CommitNode> getSelectedCommitNodes() {
    List<DefaultMutableTreeNode> selectedNodes = getSelectedTreeNodes();
    return selectedNodes.isEmpty() ? Collections.emptyList() : collectSelectedCommitNodes(selectedNodes);
  }

  @NotNull
  private List<DefaultMutableTreeNode> getSelectedTreeNodes() {
    int[] rows = myTree.getSelectionRows();
    return (rows != null && rows.length != 0) ? getNodesForRows(getSortedRows(rows)) : emptyList();
  }

  @NotNull
  private List<DefaultMutableTreeNode> getNodesForRows(@NotNull List<Integer> rows) {
    List<DefaultMutableTreeNode> nodes = ContainerUtil.newArrayList();
    for (Integer row : rows) {
      TreePath path = myTree.getPathForRow(row);
      Object pathComponent = path == null ? null : path.getLastPathComponent();
      if (pathComponent instanceof DefaultMutableTreeNode) {
        nodes.add((DefaultMutableTreeNode)pathComponent);
      }
    }
    return nodes;
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0 && pressed) {
      if (myTree.isEditing()) {
        myTree.stopEditing();
      }
      else {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null) {
          myTree.startEditingAtPath(TreeUtil.getPathFromRoot(node));
        }
      }
      return true;
    }
    if (myAllowSyncStrategy && e.getKeyCode() == KeyEvent.VK_F2 && e.getModifiers() == InputEvent.ALT_MASK && pressed) {
      startSyncEditing();
      return true;
    }
    if (CheckboxTreeHelper.isToggleEvent(e, myTree) && pressed) {
      toggleRepositoriesFromCommits();
      return true;
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  private void toggleRepositoriesFromCommits() {
    LinkedHashSet<CheckedTreeNode> checkedNodes = StreamEx.of(getSelectedTreeNodes())
      .map(n -> n instanceof CommitNode ? n.getParent() : n)
      .select(CheckedTreeNode.class)
      .filter(CheckedTreeNode::isEnabled)
      .toCollection(LinkedHashSet::new);
    if (checkedNodes.isEmpty()) return;
    // use new state from first lead node;
    boolean newState = !checkedNodes.iterator().next().isChecked();
    checkedNodes.forEach(n -> myTree.setNodeState(n, newState));
  }

  @Nullable
  private DefaultMutableTreeNode getFirstNodeToEdit() {
    // start edit last selected component if editable
    if (myTree.getLastSelectedPathComponent() instanceof RepositoryNode) {
      RepositoryNode selectedNode = ((RepositoryNode)myTree.getLastSelectedPathComponent());
      if (selectedNode.isEditableNow()) return selectedNode;
    }
    List<RepositoryNode> repositoryNodes = getChildNodesByType((DefaultMutableTreeNode)myTree.getModel().getRoot(),
                                                               RepositoryNode.class, false);
    RepositoryNode editableNode = ContainerUtil.find(repositoryNodes, repositoryNode -> repositoryNode.isEditableNow());
    if (editableNode != null) {
      TreeUtil.selectNode(myTree, editableNode);
    }
    return editableNode;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @NotNull
  public CheckboxTree getTree() {
    return myTree;
  }

  public void selectIfNothingSelected(@NotNull TreeNode node) {
    if (myTree.isSelectionEmpty()) {
      myTree.setSelectionPath(TreeUtil.getPathFromRoot(node));
    }
  }

  public void setChildren(@NotNull DefaultMutableTreeNode parentNode,
                          @NotNull Collection<? extends DefaultMutableTreeNode> childrenNodes) {
    parentNode.removeAllChildren();
    for (DefaultMutableTreeNode child : childrenNodes) {
      parentNode.add(child);
    }
    if (!myTree.isEditing()) {
      refreshNode(parentNode);
      TreePath path = TreeUtil.getPathFromRoot(parentNode);
      if (myTree.getSelectionModel().isPathSelected(path)) {
        updateChangesView();
      }
    }
    else {
      myShouldRepaint = true;
    }
  }

  private void refreshNode(@NotNull DefaultMutableTreeNode parentNode) {
    //todo should be optimized in case of start loading just edited node
    final DefaultTreeModel model = ((DefaultTreeModel)myTree.getModel());
    model.nodeStructureChanged(parentNode);
    autoExpandChecked(parentNode);
    myShouldRepaint = false;
  }

  private void autoExpandChecked(@NotNull DefaultMutableTreeNode node) {
    if (node.getChildCount() <= 0) return;
    if (node instanceof RepositoryNode) {
      expandIfChecked((RepositoryNode)node);
      return;
    }
    for (DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node.getFirstChild();
         childNode != null;
         childNode = (DefaultMutableTreeNode)node.getChildAfter(childNode)) {
      if (!(childNode instanceof RepositoryNode)) return;
      expandIfChecked((RepositoryNode)childNode);
    }
  }

  private void expandIfChecked(@NotNull RepositoryNode node) {
    if (node.isChecked()) {
      TreePath path = TreeUtil.getPathFromRoot(node);
      myTree.expandPath(path);
    }
  }

  private void setSyncText(String value) {
    mySyncRenderedText = value;
  }

  public void fireEditorUpdated(@NotNull String currentText) {
    if (mySyncStrategy) {
      //update ui model
      List<RepositoryNode> repositoryNodes =
        getChildNodesByType((DefaultMutableTreeNode)myTree.getModel().getRoot(), RepositoryNode.class, false);
      for (RepositoryNode node : repositoryNodes) {
        if (node.isEditableNow()) {
          node.forceUpdateUiModelWithTypedText(currentText);
        }
      }
      setSyncText(currentText);
      myTree.repaint();
    }
  }

  private void resetEditSync() {
    if (mySyncStrategy) {
      mySyncStrategy = false;
      mySyncRenderedText = null;
    }
  }

  private class MyTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return;
      }
      myCheckbox.setBorder(null); //checkBox may have no border by default, but insets are not null,
      // it depends on LaF, OS and isItRenderedPane, see com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxBorder.
      // null border works as expected always.
      if (value instanceof RepositoryNode) {
        //todo simplify, remove instance of
        RepositoryNode valueNode = (RepositoryNode)value;
        myCheckbox.setVisible(valueNode.isCheckboxVisible());
        if (valueNode.isChecked() && valueNode.isLoading()) {
          myCheckbox.setState(ThreeStateCheckBox.State.DONT_CARE);
        }
        else {
          myCheckbox.setSelected(valueNode.isChecked());
        }
      }
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      ColoredTreeCellRenderer renderer = getTextRenderer();
      if (value instanceof CustomRenderedTreeNode) {
        if (tree.isEditing() && mySyncStrategy && value instanceof RepositoryNode) {
          //sync rendering all editable fields
          ((RepositoryNode)value).render(renderer, mySyncRenderedText);
        }
        else {
          ((CustomRenderedTreeNode)value).render(renderer);
        }
      }
      else {
        renderer.append(userObject == null ? "" : userObject.toString());
      }
    }
  }

  private class MyTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private RepositoryWithBranchPanel myValue;

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      RepositoryWithBranchPanel panel = (RepositoryWithBranchPanel)((DefaultMutableTreeNode)value).getUserObject();
      myValue = panel;
      myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, false, true);
      return panel.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row, true);
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        MouseEvent me = ((MouseEvent)anEvent);
        final TreePath path = myTree.getClosestPathForLocation(me.getX(), me.getY());
        final int row = myTree.getRowForLocation(me.getX(), me.getY());
        myTree.getCellRenderer().getTreeCellRendererComponent(myTree, path.getLastPathComponent(), false, false, true, row, true);
        Object tag = me.getClickCount() >= 1
                     ? PushLogTreeUtil.getTagAtForRenderer(myTreeCellRenderer, me)
                     : null;
        return tag instanceof VcsEditableComponent;
      }
      //if keyboard event - then anEvent will be null =( See BasicTreeUi
      TreePath treePath = myTree.getAnchorSelectionPath();
      //there is no selection path if we start editing during initial validation//
      if (treePath == null) return true;
      Object treeNode = treePath.getLastPathComponent();
      return treeNode instanceof EditableTreeNode && ((EditableTreeNode)treeNode).isEditableNow();
    }

    public Object getCellEditorValue() {
      return myValue;
    }
  }

  private class MyTreeUi extends WideSelectionTreeUI {

    private final ComponentListener myTreeSizeListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // invalidate, revalidate etc may have no 'size' effects, you need to manually invalidateSizes before.
        updateSizes();
      }
    };

    private final AncestorListener myTreeAncestorListener = new AncestorListenerAdapter() {
      @Override
      public void ancestorMoved(AncestorEvent event) {
        super.ancestorMoved(event);
        updateSizes();
      }
    };

    private void updateSizes() {
      treeState.invalidateSizes();
      tree.repaint();
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      tree.addComponentListener(myTreeSizeListener);
      tree.addAncestorListener(myTreeAncestorListener);
    }


    @Override
    protected void uninstallListeners() {
      tree.removeComponentListener(myTreeSizeListener);
      tree.removeAncestorListener(myTreeAncestorListener);
      super.uninstallListeners();
    }

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
      return new NodeDimensionsHandler() {
        @Override
        public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
          Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
          dimensions.width = Math.max(
            myScrollPane != null ? myScrollPane.getViewport().getWidth() - getRowX(row, depth) : myTree.getMinimumSize().width,
            dimensions.width);
          return dimensions;
        }
      };
    }
  }

  private static class MyTreeViewPort extends JBViewport {

    final int myHeightToReduce;

    public MyTreeViewPort(@Nullable Component view, int heightToReduce) {
      super();
      setView(view);
      myHeightToReduce = heightToReduce;
    }

    @Override
    public Dimension getExtentSize() {
      Dimension defaultSize = super.getExtentSize();
      return new Dimension(defaultSize.width, defaultSize.height - myHeightToReduce);
    }
  }
}


