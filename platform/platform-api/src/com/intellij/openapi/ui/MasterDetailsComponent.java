/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
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

/**
 * @author anna
 * @since 29-May-2006
 */
public abstract class MasterDetailsComponent implements Configurable, DetailsComponent.Facade, MasterDetails {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.MasterDetailsComponent");

  protected static final Icon COPY_ICON = PlatformIcons.COPY_ICON;

  protected NamedConfigurable myCurrentConfigurable;
  private final JBSplitter mySplitter;

  @NonNls public static final String TREE_OBJECT = "treeObject";
  @NonNls public static final String TREE_NAME = "treeName";

  protected History myHistory = new History(new Place.Navigator() {
    public void setHistory(final History history) {
      myHistory = history;
    }

    @Nullable
    public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
      return null;
    }

    public void queryPlace(@NotNull final Place place) {
    }
  });
  private JComponent myMaster;

  public void setHistory(final History history) {
    myHistory = history;
  }

  protected final MasterDetailsState myState;

  protected Runnable TREE_UPDATER;

  {
    TREE_UPDATER = new Runnable() {
      public void run() {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath == null) return;

        MyNode node = (MyNode)selectionPath.getLastPathComponent();
        if (node == null) return;

        myState.setLastEditedConfigurable(getNodePathString(node)); //survive after rename;
        myDetails.setText(node.getConfigurable().getBannerSlogan());
        ((DefaultTreeModel)myTree.getModel()).reload(node);
        fireItemsChangedExternally();
      }
    };
  }

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

  protected MasterDetailsComponent(MasterDetailsState state) {
    myState = state;

    mySplitter = new OnePixelSplitter(false, .2f);
    mySplitter.setSplitterProportionKey("ProjectStructure.SecondLevelElements");
    mySplitter.setHonorComponentsMinimumSize(true);

    installAutoScroll();
    reInitWholePanelIfNeeded();
  }

  protected void reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) return;

    myWholePanel = new JPanel(new BorderLayout()) {
      public void addNotify() {
        super.addNotify();
        MasterDetailsComponent.this.addNotify();

        TreeModel m = myTree.getModel();
        if (m instanceof DefaultTreeModel) {
          DefaultTreeModel model = (DefaultTreeModel)m;
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
      public Dimension getMinimumSize() {
        final Dimension original = super.getMinimumSize();
        return new Dimension(Math.max(original.width, 100), original.height);
      }
    };

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree);
    DefaultActionGroup group = createToolbarActionGroup();
    if (group != null) {
      decorator.setActionGroup(group);
    }
    //left.add(myNorthPanel, BorderLayout.NORTH);
    myMaster = decorator.setAsUsualTopToolbar().setPanelBorder(JBUI.Borders.empty()).createPanel();
    myNorthPanel.setVisible(false);
    left.add(myMaster, BorderLayout.CENTER);
    mySplitter.setFirstComponent(left);

    final JPanel right = new JPanel(new BorderLayout());
    right.add(myDetails.getComponent(), BorderLayout.CENTER);

    mySplitter.setSecondComponent(right);

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myWholePanel);

    myToReInitWholePanel = false;
  }

  private void installAutoScroll() {
    myAutoScrollHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return isAutoScrollEnabled();
      }

      protected void setAutoScrollMode(boolean state) {
        //do nothing
      }

      protected void scrollToSource(Component tree) {
        updateSelectionFromTree();
      }

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
      if (!(lastPathComp instanceof MyNode)) return;
      final MyNode node = (MyNode)lastPathComp;
      setSelectedNode(node);
    } else {
      setSelectedNode(null);
    }
  }

  protected boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables) {
    return false;
  }

  public DetailsComponent getDetailsComponent() {
    return myDetails;
  }

  public Splitter getSplitter() {
    return mySplitter;
  }

  protected boolean isAutoScrollEnabled() {
    return myHistory == null || !myHistory.isNavigatingNow();
  }

  protected DefaultActionGroup createToolbarActionGroup() {
    final ArrayList<AnAction> actions = createActions(false);
    if (actions != null) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (AnAction action : actions) {
        if (action instanceof ActionGroupWithPreselection) {
          group.add(new MyActionGroupWrapper((ActionGroupWithPreselection)action));
        }
        else {
          group.add(action);
        }
      }
      return group;
    }
    return null;
  }

  public void addItemsChangeListener(ItemsChangeListener l) {
    myListeners.add(l);
  }

  protected Dimension getPanelPreferredSize() {
    return JBUI.size(800, 600);
  }

  @NotNull 
  public JComponent createComponent() {
    myTree.updateUI();
    reInitWholePanelIfNeeded();

    updateSelectionFromTree();

    final JPanel panel = new JPanel(new BorderLayout()) {
      public Dimension getPreferredSize() {
        return getPanelPreferredSize();
      }
    };
    panel.add(myWholePanel, BorderLayout.CENTER);
    return panel;
  }

  public boolean isModified() {
    if (myHasDeletedItems) return true;
    final boolean[] modified = new boolean[1];
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof MyNode) {
          final NamedConfigurable configurable = ((MyNode)node).getConfigurable();
          if (isInitialized(configurable) && configurable.isModified()) {
            modified[0] = true;
            return false;
          }
        }
        return true;
      }
    });
    return modified[0];
  }

  protected boolean isInitialized(final NamedConfigurable configurable) {
    return myInitializedConfigurables.contains(configurable);
  }

  public void apply() throws ConfigurationException {
    processRemovedItems();
    final ConfigurationException[] ex = new ConfigurationException[1];
    TreeUtil.traverse(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof MyNode) {
          try {
            final NamedConfigurable configurable = ((MyNode)node).getConfigurable();
            if (isInitialized(configurable) && configurable.isModified()) {
              configurable.apply();
            }
          }
          catch (ConfigurationException e) {
            ex[0] = e;
            return false;
          }
        }
        return true;
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    myHasDeletedItems = false;
  }

  protected abstract void processRemovedItems();

  protected abstract boolean wasObjectStored(Object editableObject);

  public void reset() {
    loadComponentState();
    myHasDeletedItems = false;
    ((DefaultTreeModel)myTree.getModel()).reload();
    //myTree.requestFocus();
    myState.getProportions().restoreSplitterProportions(myWholePanel);

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
      TreeUtil.selectFirstNode(myTree);
    }
    updateSelectionFromTree();
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

  private static String getNodePathString(final MyNode node) {
    StringBuilder path = new StringBuilder();
    MyNode current = node;
    while (current != null) {
      final Object userObject = current.getUserObject();
      if (!(userObject instanceof NamedConfigurable)) break;
      final String displayName = current.getDisplayName();
      if (StringUtil.isEmptyOrSpaces(displayName)) break;
      if (path.length() > 0) {
        path.append('|');
      }
      path.append(displayName);

      final TreeNode parent = current.getParent();
      if (!(parent instanceof MyNode)) break;
      current = (MyNode)parent;
    }
    return path.toString();
  }

  @Nullable
  @NonNls
  protected String getComponentStateKey() {
    return null;
  }

  @Nullable
  protected MasterDetailsStateService getStateService() {
    return null;
  }

  protected MasterDetailsState getState() {
    return myState;
  }

  protected void loadState(final MasterDetailsState object) {
    XmlSerializerUtil.copyBean(object, myState);
  }

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
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof MyNode) {
          final MyNode treeNode = ((MyNode)node);
          treeNode.getConfigurable().disposeUIResources();
          if (!(treeNode instanceof MyRootNode)) {
            treeNode.setUserObject(null);
          }
        }
        return true;
      }
    });
    myRoot.removeAllChildren();
  }

  @Nullable
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    return null;
  }


  protected void initTree() {
    ((DefaultTreeModel)myTree.getModel()).setRoot(myRoot);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeUtil.installActions(myTree);
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof MyNode) {
          final MyNode node = ((MyNode)value);
          setIcon(node.getIcon(expanded));
          final Font font = UIUtil.getTreeFont();
          if (node.isDisplayInBold()) {
            setFont(font.deriveFont(Font.BOLD));
          }
          else {
            setFont(font.deriveFont(Font.PLAIN));
          }
          append(node.getDisplayName(),
                 node.isDisplayInBold() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    ArrayList<AnAction> actions = createActions(true);
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
      PopupHandler
        .installPopupHandler(myTree, group, ActionPlaces.UNKNOWN, ActionManager.getInstance()); //popup should follow the selection
    }
  }

  @Nullable
  protected ArrayList<AnAction> getAdditionalActions() {
    return null;
  }

  public void fireItemsChangeListener(final Object editableObject) {
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
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 20, size.height);
        return size;
      }

      @SuppressWarnings({"NonStaticInitializer"})
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
    TreeUtil.sort(root, getNodeComparator());
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
      myTree.requestFocus();
    }
    if (nodeToSelect != null) {
      return TreeUtil.selectInTree(nodeToSelect, requestFocus, myTree, center);
    }
    else {
      return TreeUtil.selectFirstNode(myTree);
    }
  }

  @Nullable
  public Object getSelectedObject() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null && selectionPath.getLastPathComponent() instanceof MyNode) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      LOG.assertTrue(configurable != null, "already disposed");
      return configurable.getEditableObject();
    }
    return null;
  }

  @Nullable
  public NamedConfigurable getSelectedConfigurable() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      LOG.assertTrue(configurable != null, "already disposed");
      return configurable;
    }
    return null;
  }

  public void selectNodeInTree(String displayName) {
    final MyNode nodeByName = findNodeByName(myRoot, displayName);
    selectNodeInTree(nodeByName, true);
  }

  public void selectNodeInTree(final Object object) {
    selectNodeInTree(findNodeByObject(myRoot, object), true);
  }

  @Nullable
  protected static MyNode findNodeByName(final TreeNode root, final String profileName) {
    if (profileName == null) return null; //do not suggest root node
    return findNodeByCondition(root, configurable -> Comparing.strEqual(profileName, configurable.getDisplayName()));
  }

  @Nullable
  public static MyNode findNodeByObject(final TreeNode root, final Object editableObject) {
    if (editableObject == null) return null; //do not suggest root node
    return findNodeByCondition(root, configurable -> Comparing.equal(editableObject, configurable.getEditableObject()));
  }

  protected static MyNode findNodeByCondition(final TreeNode root, final Condition<NamedConfigurable> condition) {
    final MyNode[] nodeToSelect = new MyNode[1];
    TreeUtil.traverseDepth(root, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (condition.value(((MyNode)node).getConfigurable())) {
          nodeToSelect[0] = (MyNode)node;
          return false;
        }
        return true;
      }
    });
    return nodeToSelect[0];
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

  public String getHelpTopic() {
    if (myCurrentConfigurable != null) {
      return myCurrentConfigurable.getHelpTopic();
    }
    return null;
  }

  protected @Nullable String getEmptySelectionString() {
    return null;
  }

  protected void initializeConfigurable(final NamedConfigurable configurable) {
    myInitializedConfigurables.add(configurable);
  }

  /**
   * @deprecated use {@link #checkForEmptyAndDuplicatedNames(String, String, Class} instead
   */
  protected void checkApply(Set<MyNode> rootNodes, String prefix, String title) throws ConfigurationException {
    for (MyNode rootNode : rootNodes) {
      checkForEmptyAndDuplicatedNames(rootNode, prefix, title, NamedConfigurable.class, false);
    }
  }

  protected final void checkForEmptyAndDuplicatedNames(String prefix, String title,
                                                       Class<? extends NamedConfigurable> configurableClass) throws ConfigurationException {
    checkForEmptyAndDuplicatedNames(myRoot, prefix, title, configurableClass, true);
  }

  private void checkForEmptyAndDuplicatedNames(MyNode rootNode,
                                               String prefix,
                                               String title,
                                               Class<? extends NamedConfigurable> configurableClass,
                                               boolean recursively) throws ConfigurationException {
    final Set<String> names = new HashSet<>();
    for (int i = 0; i < rootNode.getChildCount(); i++) {
      final MyNode node = (MyNode)rootNode.getChildAt(i);
      final NamedConfigurable scopeConfigurable = node.getConfigurable();

      if (configurableClass.isInstance(scopeConfigurable)) {
        final String name = scopeConfigurable.getDisplayName();
        if (name.trim().length() == 0) {
          selectNodeInTree(node);
          throw new ConfigurationException("Name should contain non-space characters");
        }
        if (names.contains(name)) {
          final NamedConfigurable selectedConfigurable = getSelectedConfigurable();
          if (selectedConfigurable == null || !Comparing.strEqual(selectedConfigurable.getDisplayName(), name)) {
            selectNodeInTree(node);
          }
          throw new ConfigurationException(CommonBundle.message("smth.already.exist.error.message", prefix, name), title);
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

  protected void removeNodes(final List<MyNode> nodes) {
    MyNode parentNode = null;
    int idx = -1;
    for (MyNode node : nodes) {
      final NamedConfigurable namedConfigurable = node.getConfigurable();
      final Object editableObject = namedConfigurable.getEditableObject();
      parentNode = (MyNode)node.getParent();
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
            } else {
              toSelect = (DefaultMutableTreeNode) parentNode.getFirstChild();
            }
          } else {
            if (parentNode.isRoot() && myTree.isRootVisible()) {
              toSelect = parentNode;
            } else if (parentNode.getChildCount() > 0) {
              toSelect = (DefaultMutableTreeNode) parentNode.getFirstChild();
            }
          }
        }

        if (toSelect != null) {
          TreeUtil.selectInTree(toSelect, true, myTree);
        }
      }
      else {
        TreeUtil.selectFirstNode(myTree);
      }
    }
  }

  protected void onItemDeleted(Object item) {
  }

  protected class MyDeleteAction extends AnAction implements DumbAware {
    private final Condition<Object[]> myCondition;

    public MyDeleteAction() {
      this(Conditions.<Object[]>alwaysTrue());
    }

    public MyDeleteAction(Condition<Object[]> availableCondition) {
      super(CommonBundle.message("button.delete"), CommonBundle.message("button.delete"), PlatformIcons.DELETE_ICON);
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree);
      myCondition = availableCondition;
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final TreePath[] selectionPath = myTree.getSelectionPaths();
      if (selectionPath != null) {
        Object[] nodes = ContainerUtil.map2Array(selectionPath, treePath -> treePath.getLastPathComponent());
        if (!myCondition.value(nodes)) return;
        presentation.setEnabled(true);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      removePaths(myTree.getSelectionPaths());
    }
  }

  protected static Condition<Object[]> forAll(final Condition<Object> condition) {
    return objects -> {
      for (Object object : objects) {
        if (!condition.value(object)) return false;
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

    @NotNull
    public String getDisplayName() {
      final NamedConfigurable configurable = ((NamedConfigurable)getUserObject());
      LOG.assertTrue(configurable != null, "Tree was already disposed");
      return configurable.getDisplayName();
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

    @Nullable
    public Icon getIcon(boolean expanded) {
      // thanks to invokeLater() in TreeUtil.showAndSelect(), we can get calls to getIcon() after the tree has been disposed
      final NamedConfigurable configurable = getConfigurable();
      if (configurable != null) {
        return configurable.getIcon(expanded);
      }
      return null;
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  protected static class MyRootNode extends MyNode {
    public MyRootNode() {
      super(new NamedConfigurable(false, null) {
        public void setDisplayName(String name) {
        }

        public Object getEditableObject() {
          return null;
        }

        public String getBannerSlogan() {
          return null;
        }

        public String getDisplayName() {
          return "";
        }

        @Nullable
        @NonNls
        public String getHelpTopic() {
          return null;
        }

        public JComponent createOptionsPanel() {
          return null;
        }

        public boolean isModified() {
          return false;
        }

        public void apply() throws ConfigurationException {
        }

        public void reset() {
        }

        public void disposeUIResources() {
        }

      }, false);
    }
  }

  protected interface ItemsChangeListener {
    void itemChanged(@Nullable Object deletedItem);

    void itemsExternallyChanged();
  }

  public interface ActionGroupWithPreselection {
    ActionGroup getActionGroup();

    int getDefaultIndex();
  }

  protected class MyActionGroupWrapper extends AnAction implements DumbAware {
    private ActionGroup myActionGroup;
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

    public void actionPerformed(AnActionEvent e) {
      final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      final DataContext dataContext = e.getDataContext();
      final ListPopupStep step = popupFactory.createActionsStep(myActionGroup, dataContext, false, false,
                                                                myActionGroup.getTemplatePresentation().getText(), myTree, true,
                                                                myPreselection != null ? myPreselection.getDefaultIndex() : 0, true);
      final ListPopup listPopup = popupFactory.createListPopup(step);
      listPopup.setHandleAutoSelectionBeforeShow(true);
      if (e instanceof AnActionButton.AnActionEventWrapper) {
        ((AnActionButton.AnActionEventWrapper)e).showPopup(listPopup);
      } else {
        listPopup.showInBestPositionFor(dataContext);
      }
    }
  }

  public JComponent getToolbar() {
    myToReInitWholePanel = true;
    return myNorthPanel;
  }

  public JComponent getMaster() {
    myToReInitWholePanel = true;
    return myMaster;
  }

  public DetailsComponent getDetails() {
    myToReInitWholePanel = true;
    return myDetails;
  }

  public void initUi() {
    createComponent();
  }
}
