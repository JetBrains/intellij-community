// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

public abstract class MasterDetailsComponent implements Configurable, DetailsComponent.Facade, MasterDetails {
  protected static final Logger LOG = Logger.getInstance(MasterDetailsComponent.class);

  protected static final Icon COPY_ICON = PlatformIcons.COPY_ICON;

  protected NamedConfigurable myCurrentConfigurable;
  private final JBSplitter mySplitter;

  public static final @NonNls String TREE_OBJECT = "treeObject";
  public static final @NonNls String TREE_NAME = "treeName";

  protected History myHistory = new History(new Place.Navigator() {
    @Override
    public void setHistory(History history) {
      myHistory = history;
    }
  });

  private JComponent myMaster;

  public void setHistory(final History history) {
    myHistory = history;
  }

  protected final MasterDetailsState myState;

  protected final Runnable TREE_UPDATER = new Runnable() {
    @Override
    public void run() {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath == null) return;

      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      if (node == null) return;

      myState.setLastEditedConfigurable(getNodePathString(node)); //survive after rename;
      NamedConfigurable configurable = node.getConfigurable();
      if (configurable != null) {
        myDetails.setText(configurable.getBannerSlogan());
      }
      node.reloadNode((DefaultTreeModel)myTree.getModel());
      fireItemsChangedExternally();
    }
  };

  protected MyNode myRoot = new MyRootNode();
  protected Tree myTree = new Tree();

  private final DetailsComponent myDetails = new DetailsComponent(false, false);
  protected JPanel myWholePanel;
  public JPanel myNorthPanel = new JPanel(new BorderLayout());

  private final List<ItemsChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Set<NamedConfigurable> myInitializedConfigurables = new HashSet<>();

  private boolean myHasDeletedItems;
  protected AutoScrollToSourceHandler myAutoScrollHandler;

  protected boolean myToReInitWholePanel = true;

  protected MasterDetailsComponent() {
    this(new MasterDetailsState());
  }

  protected MasterDetailsComponent(@Nullable MasterDetailsState state) {
    myState = state == null ? new MasterDetailsState() : state;

    mySplitter = new OnePixelSplitter(false, .2f);
    mySplitter.setSplitterProportionKey("ProjectStructure.SecondLevelElements");
    mySplitter.setHonorComponentsMinimumSize(true);

    installAutoScroll();
  }

  protected void reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) {
      return;
    }

    myWholePanel = new NonOpaquePanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        MasterDetailsComponent.this.addNotify();

        TreeModel m = myTree.getModel();
        if (m instanceof DefaultTreeModel model) {
          for (int eachRow = 0; eachRow < myTree.getRowCount(); eachRow++) {
            TreePath eachPath = myTree.getPathForRow(eachRow);
            Object component = eachPath.getLastPathComponent();
            if (component instanceof TreeNode) {
              model.nodeChanged((TreeNode)component);
            }
          }
        }
      }
    };
    mySplitter.setHonorComponentsMinimumSize(true);
    myWholePanel.add(mySplitter, BorderLayout.CENTER);

    JPanel left = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        final Dimension original = super.getMinimumSize();
        return new Dimension(Math.max(original.width, 100), original.height);
      }
    };

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree)
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setPanelBorder(JBUI.Borders.empty())
      .setScrollPaneBorder(JBUI.Borders.empty());
    List<AnAction> actions = createToolbarActions();
    if (actions != null) {
      for (AnAction action : actions) {
        decorator.addExtraAction(action);
      }
    }
    //left.add(myNorthPanel, BorderLayout.NORTH);
    myMaster = decorator.createPanel();
    myNorthPanel.setVisible(false);
    left.add(myMaster, BorderLayout.CENTER);
    mySplitter.setFirstComponent(left);

    final JPanel right = new NonOpaquePanel(new BorderLayout());
    right.add(myDetails.getComponent(), BorderLayout.CENTER);

    mySplitter.setSecondComponent(right);

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myWholePanel);

    myToReInitWholePanel = false;
  }

  private void installAutoScroll() {
    myAutoScrollHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return isAutoScrollEnabled();
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        //do nothing
      }

      @RequiresEdt
      @Override
      protected void scrollToSource(@NotNull Component tree) {
        updateSelectionFromTree();
      }

      @Override
      protected boolean needToCheckFocus() {
        return false;
      }
    };
    myAutoScrollHandler.install(myTree);
  }

  protected void addNotify() {
    updateSelectionFromTree();
  }

  private void updateSelectionFromTree() {
    TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null) {
      List<NamedConfigurable> selectedConfigurables = new ArrayList<>();
      for (TreePath path : treePaths) {
        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof MyNode) {
          selectedConfigurables.add(((MyNode)lastPathComponent).getConfigurable());
        }
      }
      if (selectedConfigurables.size() > 1 && updateMultiSelection(selectedConfigurables)) {
        return;
      }
    }

    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      final Object lastPathComp = path.getLastPathComponent();
      if (!(lastPathComp instanceof MyNode node)) return;
      setSelectedNode(node);
    } else {
      setSelectedNode(null);
    }
  }

  protected boolean updateMultiSelection(final List<? extends NamedConfigurable> selectedConfigurables) {
    return false;
  }

  @Override
  public DetailsComponent getDetailsComponent() {
    return myDetails;
  }

  public Splitter getSplitter() {
    return mySplitter;
  }

  protected boolean isAutoScrollEnabled() {
    return myHistory == null || !myHistory.isNavigatingNow();
  }

  protected @Nullable List<AnAction> createToolbarActions() {
    List<AnAction> actions = createActions(false);
    if (actions == null) return null;
    return ContainerUtil.map(actions, o ->
      o instanceof ActionGroupWithPreselection oo ? new MyActionGroupWrapper(oo) : o);
  }

  public void addItemsChangeListener(ItemsChangeListener l) {
    myListeners.add(l);
  }

  protected Dimension getPanelPreferredSize() {
    return JBUI.size(800, 600);
  }

  @Override
  public @NotNull JComponent createComponent() {
    myTree.updateUI();
    reInitWholePanelIfNeeded();

    updateSelectionFromTree();

    final JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return getPanelPreferredSize();
      }
    };
    panel.add(myWholePanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public boolean isModified() {
    if (myHasDeletedItems) return true;
    return TreeUtil.treeNodeTraverser(myRoot)
      .traverse()
      .filterMap(node -> node instanceof MyNode? ((MyNode)node).getConfigurable() : null)
      .filter(configurable -> isInitialized(configurable) && configurable.isModified())
      .isNotEmpty();
  }

  protected boolean isInitialized(final NamedConfigurable configurable) {
    return myInitializedConfigurables.contains(configurable);
  }

  @Override
  public void apply() throws ConfigurationException {
    processRemovedItems();
    for (MyNode node : TreeUtil.treeNodeTraverser(myRoot).filter(MyNode.class)) {
      NamedConfigurable configurable = node.getConfigurable();
      if (isInitialized(configurable) && configurable.isModified()) {
        configurable.apply();
      }
    }
    myHasDeletedItems = false;
  }

  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  public void reset() {
    loadComponentState();
    myHasDeletedItems = false;
    ((DefaultTreeModel)myTree.getModel()).reload();
    //myTree.requestFocus();
    myState.getProportions().restoreSplitterProportions(myWholePanel);

    restoreLastSelection();
  }

  protected final void restoreLastSelection() {
    final Enumeration enumeration = myRoot.breadthFirstEnumeration();
    boolean selected = false;
    while (enumeration.hasMoreElements()) {
      final MyNode node = (MyNode)enumeration.nextElement();
      if (node instanceof MyRootNode) continue;
      final String path = getNodePathString(node);
      if (!selected && Comparing.strEqual(path, myState.getLastEditedConfigurable())) {
        TreeUtil.selectInTree(node, false, myTree);
        selected = true;
      }
    }
    if (!selected) {
      TreeUtil.promiseSelectFirst(myTree);
    }

    //'updateSelectionFromTree' initializes 'details' components and it may take some time, so if the component isn't showing now
    //  it's better to postpone calling it until 'addNotify' is called; in complex dialog like Project Structure the component may not be shown at all.
    if (myWholePanel != null && myWholePanel.isShowing()) {
      updateSelectionFromTree();
    }
  }

  protected void loadComponentState() {
    final String key = getComponentStateKey();
    final MasterDetailsStateService stateService = getStateService();
    if (key != null && stateService != null) {
      final MasterDetailsState state = stateService.getComponentState(key, myState.getClass());
      if (state != null) {
        loadState(state);
      }
    }
  }

  private static @NonNls String getNodePathString(final MyNode node) {
    @NonNls StringBuilder path = new StringBuilder();
    MyNode current = node;
    while (current != null) {
      final Object userObject = current.getUserObject();
      if (!(userObject instanceof NamedConfigurable)) break;
      final String displayName = current.getDisplayName();
      if (StringUtil.isEmptyOrSpaces(displayName)) break;
      if (!path.isEmpty()) {
        path.append('|');
      }
      path.append(displayName);

      final TreeNode parent = current.getParent();
      if (!(parent instanceof MyNode)) break;
      current = (MyNode)parent;
    }
    return path.toString();
  }

  protected @Nullable @NonNls String getComponentStateKey() {
    return null;
  }

  protected @Nullable MasterDetailsStateService getStateService() {
    return null;
  }

  protected MasterDetailsState getState() {
    return myState;
  }

  protected void loadState(final MasterDetailsState object) {
    XmlSerializerUtil.copyBean(object, myState);
  }

  @Override
  public void disposeUIResources() {
    myState.getProportions().saveSplitterProportions(myWholePanel);
    myAutoScrollHandler.cancelAllRequests();
    myDetails.disposeUIResources();
    myInitializedConfigurables.clear();
    clearChildren();
    final String key = getComponentStateKey();
    final MasterDetailsStateService stateService = getStateService();
    if (key != null && stateService != null) {
      stateService.setComponentState(key, getState());
    }
    myCurrentConfigurable = null;
  }

  protected void clearChildren() {
    for (MyNode node : TreeUtil.treeNodeTraverser(myRoot).filter(MyNode.class)) {
      node.getConfigurable().disposeUIResources();
      if (!(node instanceof MyRootNode)) {
        node.setUserObject(null);
      }
    }
    myRoot.removeAllChildren();
  }

  protected @Nullable List<AnAction> createActions(boolean fromPopup) {
    return null;
  }

  protected void initTree() {
    ((DefaultTreeModel)myTree.getModel()).setRoot(myRoot);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    TreeUtil.installActions(myTree);
    myTree.setCellRenderer(new MyColoredTreeCellRenderer());
    List<AnAction> actions = createActions(true);
    if (actions != null) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (AnAction action : actions) {
        group.add(action);
      }
      actions = getAdditionalActions();
      if (actions != null) {
        group.addSeparator();
        for (AnAction action : actions) {
          group.add(action);
        }
      }
      PopupHandler.installPopupMenu(myTree, group, "MasterDetailsTreePopup");
    }
  }

  protected @Nullable ArrayList<AnAction> getAdditionalActions() {
    return null;
  }

  private void fireItemsChangeListener(final Object editableObject) {
    for (ItemsChangeListener listener : myListeners) {
      listener.itemChanged(editableObject);
    }
  }

  private void fireItemsChangedExternally() {
    for (ItemsChangeListener listener : myListeners) {
      listener.itemsExternallyChanged();
    }
  }

  private void createUIComponents() {
    myTree = new Tree() {
      @Override
      public JToolTip createToolTip() {
        final JToolTip toolTip = new JToolTip() {
          {
            setUI(new MultiLineTooltipUI());
          }
        };
        toolTip.setComponent(this);
        return toolTip;
      }
    };
  }

  protected void addNode(MyNode nodeToAdd, MyNode parent) {
    int i = TreeUtil.indexedBinarySearch(parent, nodeToAdd, getNodeComparator());
    int insertionPoint = i >= 0 ? i : -i - 1;
    ((DefaultTreeModel)myTree.getModel()).insertNodeInto(nodeToAdd, parent, insertionPoint);
  }

  protected void sortDescendants(MyNode root) {
    TreeUtil.sortRecursively(root, getNodeComparator());
    ((DefaultTreeModel)myTree.getModel()).reload(root);
  }

  protected Comparator<MyNode> getNodeComparator() {
    return (o1, o2) -> StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());
  }

  public ActionCallback selectNodeInTree(final DefaultMutableTreeNode nodeToSelect) {
    return selectNodeInTree(nodeToSelect, true, false);
  }

  public ActionCallback selectNodeInTree(final DefaultMutableTreeNode nodeToSelect, boolean requestFocus) {
    return selectNodeInTree(nodeToSelect, true, requestFocus);
  }

  public ActionCallback selectNodeInTree(final DefaultMutableTreeNode nodeToSelect, boolean center, final boolean requestFocus) {
    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
    }
    if (nodeToSelect != null) {
      return TreeUtil.selectInTree(nodeToSelect, requestFocus, myTree, center);
    }
    return TreeUtil.selectFirstNode(myTree);
  }

  public @Nullable Object getSelectedObject() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null && selectionPath.getLastPathComponent() instanceof MyNode node) {
      NamedConfigurable<?> configurable = node.getConfigurable();
      if (configurable == null) {
        LOG.error("already disposed");
      }
      return configurable == null ? null : configurable.getEditableObject();
    }
    return null;
  }

  public final MyNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    return path != null ? (MyNode)path.getLastPathComponent() : null;
  }

  public @Nullable NamedConfigurable getSelectedConfigurable() {
    MyNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      final NamedConfigurable configurable = selectedNode.getConfigurable();
      LOG.assertTrue(configurable != null, "already disposed");
      return configurable;
    }
    return null;
  }

  public void selectNodeInTree(@NlsSafe String displayName) {
    final MyNode nodeByName = findNodeByName(myRoot, displayName);
    selectNodeInTree(nodeByName, true);
  }

  public void selectNodeInTree(final Object object) {
    selectNodeInTree(findNodeByObject(myRoot, object), true);
  }

  protected static @Nullable MyNode findNodeByName(final TreeNode root, final String profileName) {
    if (profileName == null) return null; //do not suggest root node
    return findNodeByCondition(root, configurable -> Comparing.strEqual(profileName, configurable.getDisplayName()));
  }

  public static @Nullable MyNode findNodeByObject(final TreeNode root, final Object editableObject) {
    if (editableObject == null) return null; //do not suggest root node
    return findNodeByCondition(root, configurable -> Comparing.equal(editableObject, configurable.getEditableObject()));
  }

  protected static MyNode findNodeByCondition(final TreeNode root, final Condition<? super NamedConfigurable> condition) {
    return TreeUtil.treeNodeTraverser(root)
      .filter(MyNode.class)
      .filter(node -> condition.value(node.getConfigurable()))
      .first();
  }

  protected void setSelectedNode(@Nullable MyNode node) {
    if (node != null) {
      myState.setLastEditedConfigurable(getNodePathString(node));
    }
    updateSelection(node != null ? node.getConfigurable() : null);
  }

  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    myDetails.setText(configurable != null ? configurable.getBannerSlogan() : null);

    myCurrentConfigurable = configurable;

    if (configurable != null) {
      final JComponent comp = configurable.createComponent();
      if (comp == null) {
        setEmpty();
        LOG.error("createComponent() returned null. configurable=" + configurable);
      } else {
        myDetails.setContent(comp);
        ensureInitialized(configurable);
        myHistory.pushPlaceForElement(TREE_OBJECT, configurable.getEditableObject());
      }
    } else {
      setEmpty();
    }
  }

  public void ensureInitialized(NamedConfigurable configurable) {
    if (!isInitialized(configurable)) {
      configurable.reset();
      initializeConfigurable(configurable);
    }
  }

  private void setEmpty() {
    myDetails.setContent(null);
    myDetails.setEmptyContentText(getEmptySelectionString());
  }

  @Override
  public String getHelpTopic() {
    if (myCurrentConfigurable != null) {
      return myCurrentConfigurable.getHelpTopic();
    }
    return null;
  }

  protected @NlsContexts.StatusText @Nullable String getEmptySelectionString() {
    return null;
  }

  protected void initializeConfigurable(final NamedConfigurable configurable) {
    myInitializedConfigurables.add(configurable);
  }

  protected final void checkForEmptyAndDuplicatedNames(String prefix, String title,
                                                       Class<? extends NamedConfigurable<?>> configurableClass) throws ConfigurationException {
    checkForEmptyAndDuplicatedNames(myRoot, prefix, title, configurableClass, true);
  }

  private void checkForEmptyAndDuplicatedNames(MyNode rootNode,
                                               String prefix,
                                               String title,
                                               Class<? extends NamedConfigurable<?>> configurableClass,
                                               boolean recursively) throws ConfigurationException {
    final Set<String> names = new HashSet<>();
    for (int i = 0; i < rootNode.getChildCount(); i++) {
      final MyNode node = (MyNode)rootNode.getChildAt(i);
      final NamedConfigurable scopeConfigurable = node.getConfigurable();

      if (configurableClass.isInstance(scopeConfigurable)) {
        final String name = scopeConfigurable.getDisplayName();
        if (name.trim().isEmpty()) {
          selectNodeInTree(node);
          throw new ConfigurationException(UIBundle.message("master.detail.err.empty.name"));
        }
        if (names.contains(name)) {
          final NamedConfigurable selectedConfigurable = getSelectedConfigurable();
          if (selectedConfigurable == null || !Comparing.strEqual(selectedConfigurable.getDisplayName(), name)) {
            selectNodeInTree(node);
          }
          throw new ConfigurationException(UIBundle.message("master.detail.err.duplicate", prefix, name), title);
        }
        names.add(name);
      }

      if (recursively) {
        checkForEmptyAndDuplicatedNames(node, prefix, title, configurableClass, true);
      }
    }
  }

  public Tree getTree() {
    return myTree;
  }

  protected void removePaths(final TreePath... paths) {
    List<MyNode> nodes = new ArrayList<>();
    for (TreePath path : paths) {
      nodes.add((MyNode)path.getLastPathComponent());
    }
    removeNodes(nodes);
  }

  protected void removeNodes(final List<? extends MyNode> nodes) {
    MyNode parentNode = null;
    int idx = -1;
    for (MyNode node : nodes) {
      final NamedConfigurable namedConfigurable = node.getConfigurable();
      final Object editableObject = namedConfigurable.getEditableObject();
      parentNode = (MyNode)node.getParent();
      if (parentNode == null) continue;
      idx = parentNode.getIndex(node);
      ((DefaultTreeModel)myTree.getModel()).removeNodeFromParent(node);
      myHasDeletedItems |= wasObjectStored(editableObject);
      fireItemsChangeListener(editableObject);
      onItemDeleted(editableObject);
      namedConfigurable.disposeUIResources();
    }

    if (!nodes.isEmpty()) {
      if (parentNode != null && idx != -1) {
        DefaultMutableTreeNode toSelect = null;
        if (idx < parentNode.getChildCount()) {
          toSelect = (DefaultMutableTreeNode) parentNode.getChildAt(idx);
        } else {
          if (idx > 0 && parentNode.getChildCount() > 0) {
            if (idx - 1 < parentNode.getChildCount()) {
              toSelect = (DefaultMutableTreeNode) parentNode.getChildAt(idx - 1);
            }
            else {
              toSelect = (DefaultMutableTreeNode) parentNode.getFirstChild();
            }
          }
          else {
            if (parentNode.isRoot() && myTree.isRootVisible()) {
              toSelect = parentNode;
            }
            else if (parentNode.getChildCount() > 0) {
              toSelect = (DefaultMutableTreeNode) parentNode.getFirstChild();
            }
          }
        }

        if (toSelect != null) {
          TreeUtil.selectInTree(toSelect, true, myTree);
        }
      }
      else {
        TreeUtil.promiseSelectFirst(myTree);
      }
    }
  }

  protected void onItemDeleted(Object item) {
  }

  public static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MyNode node) {
        renderIcon(node, expanded);
        renderName(node);
      }
    }

    protected void renderIcon(@NotNull MyNode node, boolean expanded) {
      setIcon(node.getIcon(expanded));
    }

    protected void renderName(@NotNull MyNode node) {
      final Font font = UIUtil.getTreeFont();
      if (node.isDisplayInBold()) {
        setFont(font.deriveFont(Font.BOLD));
      }
      else {
        setFont(font.deriveFont(Font.PLAIN));
      }

      SimpleTextAttributes attributes = node.isDisplayInBold() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES :
                                        SimpleTextAttributes.REGULAR_ATTRIBUTES;
      append(node.getDisplayName(), SimpleTextAttributes.merge(getAdditionalAttributes(node), attributes));
      String locationString = node.getLocationString();
      if (locationString != null) {
        append(" ");
        append(locationString, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }

    protected @NotNull SimpleTextAttributes getAdditionalAttributes(@NotNull MyNode node) {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  protected class MyDeleteAction extends AnAction implements DumbAware {
    private final @Nullable Predicate<Object[]> myCondition;

    public MyDeleteAction() {
      this(null);
    }

    public MyDeleteAction(@Nullable Predicate<Object[]> availableCondition) {
      super(CommonBundle.messagePointer("button.delete"), CommonBundle.messagePointer("button.delete"), PlatformIcons.DELETE_ICON);
      registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.REMOVE), myTree);
      myCondition = availableCondition;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final TreePath[] selectionPath = myTree.getSelectionPaths();
      if (selectionPath == null) {
        return;
      }

      if (myCondition != null) {
        Object[] result = new Object[selectionPath.length];
        for (int i = 0; i < selectionPath.length; i++) {
          result[i] = selectionPath[i].getLastPathComponent();
        }
        if (!myCondition.test(result)) {
          return;
        }
      }
      presentation.setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      removePaths(myTree.getSelectionPaths());
    }
  }

  protected static Predicate<Object[]> forAll(@NotNull Predicate<Object> condition) {
    return objects -> {
      for (Object object : objects) {
        if (!condition.test(object)) {
          return false;
        }
      }
      return true;
    };
  }

  public static class MyNode extends DefaultMutableTreeNode {
    private boolean myDisplayInBold;

    public MyNode(@NotNull NamedConfigurable userObject) {
      super(userObject);
    }

    public MyNode(@NotNull NamedConfigurable userObject, boolean displayInBold) {
      super(userObject);
      myDisplayInBold = displayInBold;
    }

    public @NotNull @NlsSafe String getDisplayName() {
      final NamedConfigurable configurable = (NamedConfigurable)getUserObject();
      if (configurable != null) return configurable.getDisplayName();
      LOG.debug("Tree was already disposed"); // workaround for IDEA-206547
      return "DISPOSED";
    }

    public @Nullable @NlsContexts.PopupTitle String getLocationString() {
      return null;
    }

    public NamedConfigurable getConfigurable() {
      return (NamedConfigurable)getUserObject();
    }

    public boolean isDisplayInBold() {
      return myDisplayInBold;
    }

    public void setDisplayInBold(boolean displayInBold) {
      myDisplayInBold = displayInBold;
    }

    public @Nullable Icon getIcon(boolean expanded) {
      // thanks to invokeLater() in TreeUtil.showAndSelect(), we can get calls to getIcon() after the tree has been disposed
      final NamedConfigurable configurable = getConfigurable();
      if (configurable != null) {
        return configurable.getIcon(expanded);
      }
      return null;
    }

    protected void reloadNode(DefaultTreeModel treeModel) {
      treeModel.reload(this);
    }
  }

  protected static class MyRootNode extends MyNode {
    public MyRootNode() {
      super(new NamedConfigurable(false, null) {
        @Override
        public void setDisplayName(String name) {
        }

        @Override
        public Object getEditableObject() {
          return null;
        }

        @Override
        public String getBannerSlogan() {
          return null;
        }

        @Override
        public String getDisplayName() {
          return "";
        }

        @Override
        public JComponent createOptionsPanel() {
          return null;
        }

        @Override
        public boolean isModified() {
          return false;
        }

        @Override
        public void apply() {
        }
      }, false);
    }
  }

  protected interface ItemsChangeListener {
    void itemChanged(@Nullable Object deletedItem);

    default void itemsExternallyChanged() {
    }
  }

  public interface ActionGroupWithPreselection {
    ActionGroup getActionGroup();

    default int getDefaultIndex() {
      return 0;
    }
  }

  protected class MyActionGroupWrapper extends AnAction implements DumbAware {
    private final ActionGroup myActionGroup;
    private ActionGroupWithPreselection myPreselection;

    public MyActionGroupWrapper(final ActionGroupWithPreselection actionGroup) {
      this(actionGroup.getActionGroup());
      myPreselection = actionGroup;
    }

    public MyActionGroupWrapper(final ActionGroup actionGroup) {
      super(actionGroup.getTemplatePresentation().getText(), actionGroup.getTemplatePresentation().getDescription(),
            actionGroup.getTemplatePresentation().getIcon());
      myActionGroup = actionGroup;
      registerCustomShortcutSet(actionGroup.getShortcutSet(), myTree);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      DataContext dataContext = e.getDataContext();
      ListPopupStep step = popupFactory.createActionsStep(
        myActionGroup, dataContext, null, false,
        false, myActionGroup.getTemplatePresentation().getText(), myTree,
        true, myPreselection != null ? myPreselection.getDefaultIndex() : 0, true);
      final ListPopup listPopup = popupFactory.createListPopup(step);
      listPopup.setHandleAutoSelectionBeforeShow(true);
      listPopup.show(JBPopupFactory.getInstance().guessBestPopupLocation(this, e));
    }
  }

  @Override
  public JComponent getToolbar() {
    myToReInitWholePanel = true;
    return myNorthPanel;
  }

  @Override
  public JComponent getMaster() {
    myToReInitWholePanel = true;
    return myMaster;
  }

  @Override
  public DetailsComponent getDetails() {
    myToReInitWholePanel = true;
    return myDetails;
  }

  @Override
  public void initUi() {
    createComponent();
  }
}
