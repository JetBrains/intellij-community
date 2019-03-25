// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.execution.dashboard.tree.RunDashboardGrouper;
import com.intellij.execution.dashboard.tree.RunDashboardStatusFilter;
import com.intellij.execution.dashboard.tree.RunDashboardTreeMouseListener;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.tree.Searchable;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

import static com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus.*;
import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

class ServiceView extends JPanel implements Disposable {
  @NonNls private static final String SERVICE_VIEW_NODE_TOOLBAR = "ServiceViewNodeToolbar";
  @NonNls private static final String SERVICE_VIEW_NODE_POPUP = "ServiceViewNodePopup";
  @NonNls private static final String SERVICE_VIEW_TREE_TOOLBAR = "ServiceViewTreeToolbar";

  private final Splitter mySplitter;
  private final JPanel myTreePanel;
  private final Tree myTree;
  private final JPanel myDetailsPanel;
  private final JBPanelWithEmptyText myMessagePanel;
  private final JComponent myToolbar;

  private final Map<Object, ServiceViewContributor.ViewDescriptor> myViewDescriptors = new HashMap<>();
  private final Map<Object, ServiceViewContributor> myContributors = new HashMap<>();
  private final Map<ServiceViewContributor, ServiceViewContributor.ViewDescriptorRenderer> myRenderers = new HashMap<>();
  private final Map<Object, ServiceViewContributor.SubtreeDescriptor> mySubtrees = new HashMap<>();
  private final Set<JComponent> myDetailsComponents = ContainerUtil.createWeakSet();

  private final MyTreeModel myTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private Object myLastSelection;
  private final RunDashboardStatusFilter myStatusFilter = new RunDashboardStatusFilter();

  @NotNull private final Project myProject;
  @NotNull private final ServiceViewState myState;

  private final RecursionGuard myGuard = RecursionManager.createGuard("ServiceView.getData");

  ServiceView(@NotNull Project project, @NotNull ServiceViewState state) {
    super(new BorderLayout());
    myProject = project;
    myState = state;

    myTree = new Tree();
    myTreeModel = new MyTreeModel();
    myAsyncTreeModel = new AsyncTreeModel(myTreeModel, this);
    myTree.setModel(myAsyncTreeModel);
    initTree();

    mySplitter = new OnePixelSplitter(false, myState.contentProportion);
    myTreePanel = new JPanel(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT), BorderLayout.CENTER);
    mySplitter.setFirstComponent(myTreePanel);
    myDetailsPanel = new JPanel(new BorderLayout());
    myMessagePanel = new JBPanelWithEmptyText().withEmptyText("Select service in tree to view details");
    myDetailsPanel.add(myMessagePanel, BorderLayout.CENTER);
    mySplitter.setSecondComponent(myDetailsPanel);
    add(mySplitter, BorderLayout.CENTER);

    myToolbar = createToolbar();
    add(myToolbar, BorderLayout.WEST);
    JComponent treeToolbar = createTreeToolBar();
    myTreePanel.add(treeToolbar, BorderLayout.NORTH);

    putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, (DataProvider)dataId -> {
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return ServiceViewManagerImpl.getToolWindowContextHelpId();
      }
      else if (PlatformDataKeys.SELECTED_ITEMS.is(dataId)) {
        return TreeUtil.collectSelectedUserObjects(myTree).toArray();
      }
      ServiceViewContributor.ViewDescriptor descriptor = getSelectedDescriptor();
      DataProvider dataProvider = descriptor == null ? null : descriptor.getDataProvider();
      if (dataProvider != null) {
        return myGuard.doPreventingRecursion(this, false, () -> dataProvider.getData(dataId));
      }
      return null;
    });

    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<JComponent>)() ->
      JBIterable.from(myDetailsComponents).filter(component -> myDetailsPanel != component.getParent()).iterator());

    myProject.getMessageBus().connect(this).subscribe(ServiceViewContributor.TOPIC, myTreeModel::refresh);
  }

  @Nullable
  private ServiceViewContributor.ViewDescriptor getSelectedDescriptor() {
    return myViewDescriptors.get(getSelectedObject());
  }

  @Nullable
  private Object getSelectedObject() {
    TreePath path = myTree.getSelectionPath();
    return TreeUtil.getLastUserObject(path);
  }

  private void initTree() {
    // look
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLineStyleAngled();
    myTree.setCellRenderer(new MyTreeCellRenderer());
    UIUtil.putClientProperty(myTree, ANIMATION_IN_RENDERER_ALLOWED, true);

    // listeners
    myTree.addTreeSelectionListener(e -> onSelectionChanged());
    new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);
    RunDashboardTreeMouseListener mouseListener = new RunDashboardTreeMouseListener(myTree);
    mouseListener.installOn(myTree);

    //RowsDnDSupport.install(myTree, myTreeModel);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (myLastSelection != null && myTreeModel.isLeaf(myLastSelection)) {
          return myViewDescriptors.get(myLastSelection).handleDoubleClick(event);
        }
        return false;
      }
    }.installOn(myTree);

    // popup
    ActionGroup actions = (ActionGroup)ActionManager.getInstance().getAction(SERVICE_VIEW_NODE_POPUP);
    PopupHandler.installPopupHandler(myTree, actions, ActionPlaces.SERVICES_POPUP, ActionManager.getInstance());

    myTreeModel.refreshAll();
    myState.treeState.applyTo(myTree, myTreeModel.getRoot());
  }

  private void setTreeVisible(boolean visible) {
    myTreePanel.setVisible(visible);
    myToolbar.setBorder(visible ? null : IdeBorderFactory.createBorder(SideBorder.RIGHT));
  }

  private void onSelectionChanged() {
    List<Object> selected = TreeUtil.collectSelectedUserObjects(myTree);
    Object newSelection = ContainerUtil.getOnlyItem(selected);
    if (Comparing.equal(newSelection, myLastSelection)) return;

    ServiceViewContributor.ViewDescriptor oldDescriptor = myLastSelection == null ? null : myViewDescriptors.get(myLastSelection);
    if (oldDescriptor != null) {
      oldDescriptor.onNodeUnselected();
    }

    myLastSelection = newSelection;
    ServiceViewContributor.ViewDescriptor newDescriptor = newSelection == null ? null : myViewDescriptors.get(newSelection);

    if (newDescriptor != null) {
      newDescriptor.onNodeSelected();
    }
    JComponent component = newDescriptor == null ? null : newDescriptor.getContentComponent();
    JComponent newDetails = component == null ? myMessagePanel : component;
    if (newDetails.getParent() == myDetailsPanel) return;

    myDetailsComponents.add(newDetails);
    myDetailsPanel.removeAll();
    myDetailsPanel.add(newDetails, BorderLayout.CENTER);
    myDetailsPanel.revalidate();
    myDetailsPanel.repaint();
  }

  private JComponent createToolbar() {
    ActionGroup actions = (ActionGroup)ActionManager.getInstance().getAction(SERVICE_VIEW_NODE_TOOLBAR);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, actions, false);
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  private JComponent createTreeToolBar() {
    JPanel toolBarPanel = new JPanel(new BorderLayout());
    toolBarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.BOTTOM));

    DefaultActionGroup treeGroup = new DefaultActionGroup();

    TreeExpander treeExpander = new MyTreeExpander();
    AnAction expandAllAction = CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this);
    treeGroup.add(expandAllAction);

    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this);
    treeGroup.add(collapseAllAction);

    treeGroup.addSeparator();
    treeGroup.add(new StatusActionGroup());

    treeGroup.addSeparator();
    AnAction treeActions = ActionManager.getInstance().getAction(SERVICE_VIEW_TREE_TOOLBAR);
    treeActions.registerCustomShortcutSet(this, null);
    treeGroup.add(treeActions);

    ActionToolbar treeActionsToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, treeGroup, true);
    toolBarPanel.add(treeActionsToolBar.getComponent(), BorderLayout.CENTER);
    treeActionsToolBar.setTargetComponent(myTree);

    return toolBarPanel;
  }

  @Override
  public void dispose() {
  }

  void updateContent(boolean withStructure) {
    myTreeModel.refreshAll();
  }

  void selectNode(Object node) {
    TreeUtil.select(myTree, new NodeSelectionVisitor(node), path -> {
    });
  }

  ServiceViewState getState() {
    myState.contentProportion = mySplitter.getProportion();
    myState.treeState = TreeState.createOn(myTree);
    return myState;
  }

  private class MyTreeExpander extends DefaultTreeExpander {
    private volatile boolean myFlat;

    MyTreeExpander() {
      super(myTree);
      myTreeModel.addTreeModelListener(new TreeModelAdapter() {
        @Override
        public void treeStructureChanged(TreeModelEvent e) {
          isFlat();
        }
      });
    }

    @Override
    public boolean canExpand() {
      return super.canExpand() && !myFlat;
    }

    @Override
    public boolean canCollapse() {
      return super.canCollapse() && !myFlat;
    }

    private void isFlat() {
      myFlat = true;
      myAsyncTreeModel.accept(new TreeVisitor() {
        @NotNull
        @Override
        public Action visit(@NotNull TreePath path) {
          if (path.getPathCount() == 1) {
            return Action.CONTINUE;
          }
          if (path.getPathCount() == 2) {
            if (myAsyncTreeModel.getChildCount(path.getLastPathComponent()) > 0) {
              myFlat = false;
              return Action.INTERRUPT;
            }
            return Action.SKIP_CHILDREN;
          }
          return Action.INTERRUPT;
        }
      });
    }
  }

  private class GroupByActionGroup extends DefaultActionGroup implements CheckedActionGroup {
    GroupByActionGroup(List<? extends RunDashboardGrouper> groupers) {
      super(ExecutionBundle.message("run.dashboard.group.by.action.name"), true);
      getTemplatePresentation().setIcon(AllIcons.Actions.GroupBy);

      for (RunDashboardGrouper grouper : groupers) {
        add(new GroupAction(grouper));
      }
    }
  }

  private class GroupAction extends ToggleAction implements DumbAware {
    private final RunDashboardGrouper myGrouper;

    GroupAction(RunDashboardGrouper grouper) {
      super();
      myGrouper = grouper;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myTreePanel.isVisible());
      ActionPresentation actionPresentation = myGrouper.getRule().getPresentation();
      presentation.setText(actionPresentation.getText());
      presentation.setDescription(actionPresentation.getDescription());
      if (ActionPlaces.SERVICES_TOOLBAR.equals(e.getPlace())) {
        presentation.setIcon(actionPresentation.getIcon());
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myGrouper.isEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myGrouper.setEnabled(state);
      updateContent(true);
    }
  }

  private class StatusActionGroup extends DefaultActionGroup implements CheckedActionGroup {
    StatusActionGroup() {
      super(ExecutionBundle.message("run.dashboard.filter.by.status.action.name"), true);
      getTemplatePresentation().setIcon(AllIcons.General.Filter);

      for (final RunDashboardRunConfigurationStatus status : new RunDashboardRunConfigurationStatus[]{STARTED, FAILED, STOPPED,
        CONFIGURED}) {
        add(new ToggleAction(status.getName()) {
          @Override
          public boolean isSelected(@NotNull AnActionEvent e) {
            return myStatusFilter.isVisible(status);
          }

          @Override
          public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
              myStatusFilter.show(status);
            }
            else {
              myStatusFilter.hide(status);
            }
            updateContent(true);
          }
        });
      }
    }
  }


  private class MyTreeModel extends BaseTreeModel<Object> implements InvokerSupplier, Searchable {
    final Object myRoot = ObjectUtils.sentinel("services root");
    final Invoker myInvoker = new Invoker.BackgroundThread(this);

    @NotNull
    @Override
    public Invoker getInvoker() {
      return myInvoker;
    }

    @NotNull
    @Override
    public Promise<TreePath> getTreePath(Object object) {
      return Promises.resolvedPromise(new TreePath(myRoot)); // TODO [konstantin.aleev]
    }

    @Override
    public boolean isLeaf(Object object) {
      return object != myRoot && !(object instanceof GroupNode) && mySubtrees.get(object) == null;
    }

    @Override
    public List<?> getChildren(Object parent) {
      if (parent == myRoot) {
        return getRootChildren();
      }
      if (parent instanceof GroupNode) {
        return new ArrayList<>(((GroupNode)parent).children);
      }

      //noinspection unchecked
      ServiceViewContributor.SubtreeDescriptor<Object> subtree = mySubtrees.get(parent);
      if (subtree == null) return Collections.emptyList();

      List<Object> result = new SmartList<>();
      for (Object item : subtree.getItems()) {
        if (item instanceof NodeDescriptor) {
          ((NodeDescriptor)item).update();
        }
        myViewDescriptors.put(item, subtree.getItemDescriptor(item));
        mySubtrees.put(item, subtree.getNodeSubtree(item));
        myContributors.put(item, myContributors.get(parent));
        result.add(item);
      }
      return result;
    }

    @NotNull
    private List<?> getRootChildren() {
      Set<Object> rootChildren = new LinkedHashSet<>();
      Map<TreePath, GroupNode> groupNodes = FactoryMap.create(GroupNode::new);
      for (@SuppressWarnings("unchecked") ServiceViewContributor<Object, Object, Object> contributor : ServiceViewManagerImpl.EP_NAME.getExtensions()) {
        for (Object node : contributor.getNodes(myProject)) {
          if (node instanceof NodeDescriptor) {
            ((NodeDescriptor)node).update();
          }
          myViewDescriptors.put(node, contributor.getNodeDescriptor(node));
          mySubtrees.put(node, contributor.getNodeSubtree(node));
          myContributors.put(node, contributor);
          List<Object> groups = contributor.getGroups(node);
          Object child = node;
          if (!groups.isEmpty()) {
            TreePath path = new TreePath(groups.toArray()).pathByAddingChild(node);
            while (path.getParentPath() != null) {
              GroupNode groupNode = groupNodes.get(path.getParentPath());
              myViewDescriptors.put(groupNode, contributor.getGroupDescriptor(groupNode.path.getLastPathComponent()));
              myContributors.put(groupNode, contributor);
              groupNode.children.add(child);
              child = groupNode;
              path = path.getParentPath();
            }
          }
          rootChildren.add(child);
        }
      }
      return new ArrayList<>(rootChildren);
    }

    @Override
    public Object getRoot() {
      return myRoot;
    }

    void refresh(ServiceViewContributor.ServiceEvent e) {
      refreshAll();
    }

    void refreshAll() {
      treeStructureChanged(null, null, null);
    }
  }

  private class MyTreeCellRenderer implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myColoredRender = new NodeRenderer() {
      @Nullable
      @Override
      protected ItemPresentation getPresentation(Object node) {
        return myViewDescriptors.get(node).getPresentation();
      }
    };
    private final ServiceViewContributor.ViewDescriptorRenderer myNodeRenderer =
      (parent, value, viewDescriptor, selected, hasFocus) -> myColoredRender.getTreeCellRendererComponent(
        (JTree)parent, value, selected, true, true, 0, hasFocus);

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      if (value == myTreeModel.getRoot() || value instanceof LoadingNode) {
        return myColoredRender;
      }
      ServiceViewContributor.ViewDescriptor nodeDescriptor = myViewDescriptors.get(value);
      ServiceViewContributor contributor = myContributors.get(value);
      if (!myRenderers.containsKey(contributor)) {
        myRenderers.put(contributor, contributor.getViewDescriptorRenderer());
      }
      ServiceViewContributor.ViewDescriptorRenderer renderer = myRenderers.get(contributor);
      Object renderedValue = value instanceof GroupNode ? ((GroupNode)value).path.getLastPathComponent() : value;
      Component component = renderer == null ? null :
                            renderer.getRendererComponent(myTree, renderedValue, nodeDescriptor, selected, hasFocus);
      if (component == null) {
        component = myNodeRenderer.getRendererComponent(myTree, value, nodeDescriptor, selected, hasFocus);
      }
      return component;
    }
  }

  private static class GroupNode {
    final TreePath path;
    final Set<Object> children = new LinkedHashSet<>();

    GroupNode(TreePath path) {
      this.path = path;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GroupNode node = (GroupNode)o;
      return Objects.equals(path, node.path);
    }

    @Override
    public int hashCode() {
      return Objects.hash(path);
    }
  }

  private static class NodeSelectionVisitor implements TreeVisitor {
    private final Object myNode;

    NodeSelectionVisitor(Object node) {
      myNode = node;
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      if (path.getLastPathComponent().equals(myNode)) return Action.INTERRUPT;

      return Action.CONTINUE;
    }
  }

  @NotNull
  private static AnAction[] doGetActions(@Nullable AnActionEvent e, boolean toolbar) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    Project project = e.getProject();
    if (project == null) return AnAction.EMPTY_ARRAY;

    ServiceView serviceView = ServiceViewManagerImpl.getServiceView(project);
    if (serviceView == null || serviceView.myLastSelection == null) return AnAction.EMPTY_ARRAY;

    ServiceViewContributor.ViewDescriptor descriptor = serviceView.myViewDescriptors.get(serviceView.myLastSelection);
    ActionGroup group = toolbar ? descriptor.getToolbarActions() : descriptor.getPopupActions();
    return group == null ? AnAction.EMPTY_ARRAY : group.getChildren(e);
  }

  public static class ItemToolbarActionGroup extends ActionGroup {
    private final static AnAction[] FAKE_GROUP = new AnAction[]{new DumbAwareAction(null, null, EmptyIcon.ICON_16) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(false);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    }};

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      AnAction[] actions = doGetActions(e, true);
      return actions.length != 0 ? actions : FAKE_GROUP;
    }
  }

  public static class ItemPopupActionGroup extends ActionGroup {
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return doGetActions(e, false);
    }
  }
}
