// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.PushSettings;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.EditSourceForDialogAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.ui.VcsLogActionIds;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COLLAPSE_ALL;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_ALL;
import static com.intellij.util.containers.ContainerUtil.emptyList;

public final class PushLog extends JPanel implements Disposable, UiDataProvider {
  @NonNls private static final String CONTEXT_MENU = "Vcs.Push.ContextMenu";
  @NonNls private static final String START_EDITING = "startEditing";
  @NonNls private static final String TREE_SPLITTER_PROPORTION = "Vcs.Push.Splitter.Tree.Proportion";
  @NonNls private static final String DETAILS_SPLITTER_PROPORTION = "Vcs.Push.Splitter.Details.Proportion";
  private final PushLogChangesBrowser myChangesBrowser;
  private final JBLoadingPanel myChangesLoadingPane;
  private final CheckboxTree myTree;
  private final MyTreeCellRenderer myTreeCellRenderer;
  private final JScrollPane myScrollPane;
  private final CommitDetailsPanel myDetailsPanel;
  private final MyShowDetailsAction myShowDetailsAction;
  private boolean myShouldRepaint = false;
  private boolean mySyncStrategy;
  @Nullable private @Nls String mySyncRenderedText;
  private final @NotNull Project myProject;
  private final boolean myAllowSyncStrategy;

  public PushLog(@NotNull Project project,
                 @NotNull CheckedTreeNode root,
                 @NotNull ModalityState modalityState,
                 boolean allowSyncStrategy) {
    myProject = project;
    myAllowSyncStrategy = allowSyncStrategy;
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    treeModel.nodeStructureChanged(root);
    myTreeCellRenderer = new MyTreeCellRenderer();
    myTree = new CheckboxTree(myTreeCellRenderer, root) {

      @Override
      protected boolean shouldShowBusyIconIfNeeded() {
        return true;
      }

      @Override
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
        if ((!(node instanceof DefaultMutableTreeNode))) {
          return "";
        }
        if (node instanceof TooltipNode) {
          String select = DvcsBundle.message("push.select.all.commit.details");
          return ((TooltipNode)node).getTooltip() + "<p style='font-style:italic;color:gray;'>" + select + "</p>"; //NON-NLS
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
        TreeSpeedSearch.installOn(this, false, path -> {
          Object pathComponent = path.getLastPathComponent();
          if (pathComponent instanceof RepositoryNode) {
            return ((RepositoryNode)pathComponent).getRepositoryName();
          }
          return pathComponent.toString();
        });
      }
    };
    myTree.setUI(new MyTreeUi());
    myTree.setBorder(JBUI.Borders.emptyTop(10));
    myTree.setEditable(true);
    myTree.setShowsRootHandles(root.getChildCount() > 1);
    MyTreeCellEditor treeCellEditor = new MyTreeCellEditor();
    myTree.setCellEditor(treeCellEditor);
    treeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof EditableTreeNode) {
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
        if (node instanceof EditableTreeNode) {
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
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        onSelectionChanges();
      }
    });
    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof RepositoryNode && myTree.isEditing()) {
          //need to force repaint foreground  for non-focused editing node
          myTree.getCellEditor().getTreeCellEditorComponent(myTree, node, true, false, false, myTree.getRowForPath(
            TreeUtil.getPathFromRoot(node)));
        }
      }
    });
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "");
    ExpandAllAction expandAllAction = new ExpandAllAction(myTree);
    expandAllAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(ACTION_EXPAND_ALL).getShortcutSet(), myTree);
    CollapseAllAction collapseAll = new CollapseAllAction(myTree);
    collapseAll.registerCustomShortcutSet(ActionManager.getInstance().getAction(ACTION_COLLAPSE_ALL).getShortcutSet(), myTree);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    PopupHandler.installPopupMenu(myTree, VcsLogActionIds.POPUP_ACTION_GROUP, CONTEXT_MENU);

    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), this,
                                              ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);

    myChangesBrowser = new PushLogChangesBrowser(project, false, false, myChangesLoadingPane);
    myChangesBrowser.hideViewerBorder();
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), myTree);
    final EditSourceForDialogAction editSourceAction = new EditSourceForDialogAction(myChangesBrowser);
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myChangesBrowser);
    myChangesBrowser.addToolbarAction(editSourceAction);
    setDefaultEmptyText();

    myDetailsPanel = new CommitDetailsPanel();
    JScrollPane detailsScrollPane =
      new JBScrollPane(myDetailsPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    detailsScrollPane.setBorder(JBUI.Borders.empty());
    detailsScrollPane.setViewportBorder(JBUI.Borders.empty());
    BorderLayoutPanel detailsContentPanel = new BorderLayoutPanel();
    detailsContentPanel.addToCenter(detailsScrollPane);

    JBSplitter detailsSplitter = new OnePixelSplitter(true, DETAILS_SPLITTER_PROPORTION, 0.67f);
    detailsSplitter.setFirstComponent(myChangesLoadingPane);
    myChangesLoadingPane.add(myChangesBrowser);

    myShowDetailsAction = new MyShowDetailsAction(project, (state) -> {
      detailsSplitter.setSecondComponent(state ? detailsContentPanel : null);
    });
    myShowDetailsAction.setEnabled(false);
    myChangesBrowser.addToolbarSeparator();
    myChangesBrowser.addToolbarAction(myShowDetailsAction);

    JBSplitter splitter = new OnePixelSplitter(TREE_SPLITTER_PROPORTION, 0.5f);
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
    myScrollPane.setBorder(JBUI.Borders.empty());
    splitter.setFirstComponent(myScrollPane);
    splitter.setSecondComponent(detailsSplitter);

    setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);
    myTree.setRowHeight(0);
  }

  @Override
  public void dispose() {
    myChangesBrowser.shutdown();
  }

  public void highlightNodeOrFirst(@Nullable RepositoryNode repositoryNode, boolean shouldScrollTo) {
    TreePath selectionPath = repositoryNode != null ? TreeUtil.getPathFromRoot(repositoryNode) : TreeUtil.getFirstNodePath(myTree);
    myTree.setSelectionPath(selectionPath);
    if (shouldScrollTo) {
      myTree.scrollPathToVisible(selectionPath);
    }
  }

  private void restoreSelection(@Nullable DefaultMutableTreeNode node) {
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
    }
  }

  private JComponent createStrategyPanel() {
    final JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBackground(RenderingUtil.getBackground(myTree));
    final LinkLabel<String> linkLabel = new LinkLabel<>(DvcsBundle.message("push.edit.all.targets"), null);
    linkLabel.setBorder(JBUI.Borders.empty(2));
    linkLabel.setListener((aSource, aLinkData) -> {
      if (linkLabel.isEnabled()) {
        startSyncEditing();
      }
    }, null);
    myTree.addPropertyChangeListener(PushLogTreeUtil.EDIT_MODE_PROP, evt -> {
      Boolean editMode = (Boolean)evt.getNewValue();
      linkLabel.setEnabled(!editMode);
      linkLabel.setPaintUnderline(!editMode);
      linkLabel.repaint();
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
  static List<Change> collectAllChanges(@NotNull List<? extends CommitNode> commitNodes) {
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
  private static List<Change> collectChanges(@NotNull List<? extends CommitNode> commitNodes) {
    List<Change> changes = new ArrayList<>();
    for (CommitNode node : commitNodes) {
      changes.addAll(node.getUserObject().getChanges());
    }
    return changes;
  }

  @NotNull
  private static <T> List<T> getChildNodesByType(@NotNull DefaultMutableTreeNode node, Class<T> type, boolean reverseOrder) {
    List<T> nodes = new ArrayList<>();
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
  private static List<Integer> getSortedRows(int @NotNull [] rows) {
    List<Integer> sorted = new ArrayList<>();
    for (int row : rows) {
      sorted.add(row);
    }
    sorted.sort(Collections.reverseOrder());
    return sorted;
  }

  public void setBusyLoading(boolean paintBusy) {
    myTree.setPaintBusy(paintBusy);
  }

  private void onSelectionChanges() {
    List<CommitNode> commitNodes = getSelectedCommitNodes();
    updateChangesView(commitNodes);
    updateDetailsPanel(commitNodes);
  }

  private void updateChangesView(@NotNull List<? extends CommitNode> commitNodes) {
    if (!commitNodes.isEmpty()) {
      myChangesBrowser.getViewer().setEmptyText(DvcsBundle.message("push.no.differences"));
    }
    else {
      setDefaultEmptyText();
    }

    myChangesBrowser.setCommitsToDisplay(commitNodes);
  }

  private void updateDetailsPanel(@NotNull List<? extends CommitNode> commitNodes) {
    if (commitNodes.size() == 1 && getSelectedTreeNodes().stream().noneMatch(it -> it instanceof RepositoryNode)) {
      VcsFullCommitDetails commitDetails = commitNodes.get(0).getUserObject();
      CommitPresentationUtil.CommitPresentation presentation =
        CommitPresentationUtil.buildPresentation(myProject, commitDetails, new HashSet<>());
      myDetailsPanel.setCommit(presentation);
      myShowDetailsAction.setEnabled(true);
    }
    else {
      myShowDetailsAction.setEnabled(false);
    }
  }

  private void setDefaultEmptyText() {
    myChangesBrowser.getViewer().setEmptyText(DvcsBundle.message("push.no.commits.selected"));
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    // Make changes available for diff action; revisionNumber for create patch and copy revision number actions
    List<CommitNode> commitNodes = getSelectedCommitNodes();
    sink.set(VcsDataKeys.CHANGES,
             collectAllChanges(commitNodes).toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(VcsDataKeys.VCS_REVISION_NUMBERS, ContainerUtil.map2Array(
      commitNodes, VcsRevisionNumber.class, commitNode -> {
        Hash hash = commitNode.getUserObject().getId();
        return new TextRevisionNumber(hash.asString(), hash.toShortString());
      }));
    sink.set(VcsDataKeys.VCS_COMMIT_SUBJECTS, ContainerUtil.map2Array(
      commitNodes, String.class, commitNode -> commitNode.getUserObject().getSubject()));
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
    List<DefaultMutableTreeNode> nodes = new ArrayList<>();
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
    if (e.getKeyCode() == KeyEvent.VK_ENTER && myTree.isEditing() && e.getModifiersEx() == 0 && pressed) {
      myTree.stopEditing();
      return true;
    }
    if (myAllowSyncStrategy && e.getKeyCode() == KeyEvent.VK_F2 && e.getModifiersEx() == InputEvent.ALT_DOWN_MASK && pressed) {
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
    if (myTree.getLastSelectedPathComponent() instanceof RepositoryNode selectedNode) {
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
        onSelectionChanges();
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

  private void setSyncText(@Nls String value) {
    mySyncRenderedText = value;
  }

  public void fireEditorUpdated(@NotNull @Nls String currentText) {
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
      ColoredTreeCellRenderer renderer = getTextRenderer();
      renderer.setIpad(JBInsets.emptyInsets());
      if (value instanceof RepositoryNode valueNode) {
        //todo simplify, remove instance of
        boolean isCheckboxVisible = valueNode.isCheckboxVisible();
        myCheckbox.setVisible(isCheckboxVisible);
        if (!isCheckboxVisible) {
          // if we don't set right inset, "new" icon will be cropped
          renderer.setIpad(JBUI.insets(0, 10));
        }
        if (valueNode.isChecked() && valueNode.isLoading()) {
          myCheckbox.setState(ThreeStateCheckBox.State.DONT_CARE);
        }
        else {
          myCheckbox.setSelected(valueNode.isChecked());
        }
      }
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
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
        renderer.append(userObject == null ? "" : userObject.toString()); //NON-NLS
      }
    }
  }

  private class MyTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private RepositoryWithBranchPanel<?> myValue;

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      RepositoryWithBranchPanel<?> panel = (RepositoryWithBranchPanel<?>)((DefaultMutableTreeNode)value).getUserObject();
      myValue = panel;
      myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, false, true);
      return panel.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row, true);
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent me) {
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

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }
  }

  private class MyTreeUi extends WideSelectionTreeUI {

    private final ComponentListener myTreeSizeListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // invalidate, revalidate etc. may have no 'size' effects, you need to manually invalidateSizes before.
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

    MyTreeViewPort(@Nullable Component view, int heightToReduce) {
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

  private static class MyShowDetailsAction extends DumbAwareToggleAction {
    private boolean myEnabled;
    @NotNull private final PushSettings mySettings;
    private final @NotNull Consumer<? super Boolean> myOnUpdate;

    MyShowDetailsAction(@NotNull Project project, @NotNull Consumer<? super Boolean> onUpdate) {
      super(DvcsBundle.message("push.show.details"), null, AllIcons.Actions.PreviewDetailsVertically);
      mySettings = project.getService(PushSettings.class);
      myOnUpdate = onUpdate;
    }

    private boolean getValue() {
      return mySettings.getShowDetailsInPushDialog();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return getValue();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      mySettings.setShowDetailsInPushDialog(state);
      myOnUpdate.accept(state);
    }

    void setEnabled(boolean enabled) {
      myOnUpdate.accept(enabled && getValue());
      myEnabled = enabled;
    }
  }
}


