// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.todo.nodes.TodoTreeHelper;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.*;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;

public abstract class TodoPanel extends SimpleToolWindowPanel implements OccurenceNavigator, DataProvider, Disposable {

  protected static final Logger LOG = Logger.getInstance(TodoPanel.class);

  protected final @NotNull TodoView myTodoView;
  protected final @NotNull Project myProject;
  private final @NotNull TodoPanelSettings mySettings;
  private final boolean myCurrentFileMode;
  private final @NotNull Content myContent;

  private final @NotNull Tree myTree;
  private final @NotNull TreeExpander myTreeExpander;
  private final @NotNull MyOccurenceNavigator myOccurenceNavigator;
  private final @NotNull TodoTreeBuilder myTodoTreeBuilder;

  private final @NotNull UsagePreviewPanel myUsagePreviewPanel;
  private MyAutoScrollToSourceHandler myAutoScrollToSourceHandler;

  private final TodoPanelCoroutineHelper myCoroutineHelper = new TodoPanelCoroutineHelper(this);

  public static final DataKey<TodoPanel> TODO_PANEL_DATA_KEY = DataKey.create("TodoPanel");

  /**
   * @param currentFileMode if {@code true} then view doesn't have "Group By Packages" and "Flatten Packages"
   *                        actions.
   */
  public TodoPanel(@NotNull TodoView todoView, @NotNull TodoPanelSettings settings, boolean currentFileMode, @NotNull Content content) {
    super(false, true);

    myTodoView = todoView;
    myProject = todoView.getProject();
    mySettings = settings;
    myCurrentFileMode = currentFileMode;
    myContent = content;

    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);
    myTreeExpander = new DefaultTreeExpander(myTree);
    myOccurenceNavigator = new MyOccurenceNavigator();

    myUsagePreviewPanel = new UsagePreviewPanel(myProject,
                                                FindInProjectUtil.setupViewPresentation(false, new FindModel()));
    Disposer.register(this, myUsagePreviewPanel);

    initUI();

    myTodoTreeBuilder = setupTreeStructure();
    Disposer.register(this, myTodoTreeBuilder);

    updateTodoFilter();
    myTodoTreeBuilder.setShowPackages(mySettings.arePackagesShown);
    myTodoTreeBuilder.setShowModules(mySettings.areModulesShown);
    myTodoTreeBuilder.setFlattenPackages(mySettings.areFlattenPackages);
  }

  private @NotNull TodoTreeBuilder setupTreeStructure() {
    TodoTreeBuilder todoTreeBuilder = createTreeBuilder(myTree, myProject);

    TodoTreeStructure structure = todoTreeBuilder.getTodoTreeStructure();
    StructureTreeModel<? extends TodoTreeStructure> structureTreeModel = new StructureTreeModel<>(structure,
                                                                                                  TodoTreeBuilder.NODE_DESCRIPTOR_COMPARATOR,
                                                                                                  myProject);
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(structureTreeModel, myProject);
    myTree.setModel(asyncTreeModel);
    asyncTreeModel.addTreeModelListener(new MyExpandListener(todoTreeBuilder));
    todoTreeBuilder.setModel(structureTreeModel);
    Object selectableElement = structure.getFirstSelectableElement();
    if (selectableElement != null) {
      todoTreeBuilder.select(selectableElement);
    }
    return todoTreeBuilder;
  }

  protected final @NotNull Tree getTree() {
    return myTree;
  }

  protected final @NotNull TodoTreeBuilder getTreeBuilder() {
    return myTodoTreeBuilder;
  }

  protected final @NotNull UsagePreviewPanel getUsagePreviewPanel() {
    return myUsagePreviewPanel;
  }

  private final class MyExpandListener extends TreeModelAdapter {

    private final TodoTreeBuilder myBuilder;

    MyExpandListener(TodoTreeBuilder builder) {
      myBuilder = builder;
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      TreePath parentPath = e.getTreePath();
      if (parentPath == null || parentPath.getPathCount() > 2) return;
      Object[] children = e.getChildren();
      for (Object o : children) {
        NodeDescriptor descriptor = TreeUtil.getUserObject(NodeDescriptor.class, o);
        if (descriptor != null && myBuilder.isAutoExpandNode(descriptor)) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (myTree.isVisible(parentPath) && myTree.isExpanded(parentPath)) {
              myTree.expandPath(parentPath.pathByAddingChild(o));
            }
          }, myBuilder.myProject.getDisposed());
        }
      }
    }
  }

  protected abstract TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                       @NotNull Project project);

  private void initUI() {
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
    myTree.setRowHeight(0); // enable variable-height rows
    myTree.setCellRenderer(new TodoCompositeRenderer());
    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.addSeparator();
    group.add(CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this));
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    PopupHandler.installPopupMenu(myTree, group, ActionPlaces.TODO_VIEW_POPUP);

    myUsagePreviewPanel.setVisible(mySettings.showPreview);

    setContent(createCenterComponent());

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent e) {
        myCoroutineHelper.schedulePreviewPanelLayoutUpdate();
      }
    });

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    myAutoScrollToSourceHandler.install(myTree);

    // Create tool bars and register custom shortcuts

    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new PreviousOccurenceToolbarAction(myOccurenceNavigator));
    toolbarGroup.add(new NextOccurenceToolbarAction(myOccurenceNavigator));
    toolbarGroup.add(new SetTodoFilterAction(myProject, mySettings, todoFilter -> setTodoFilter(todoFilter)));
    toolbarGroup.add(createAutoScrollToSourceAction());
    toolbarGroup.add(CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this));
    toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this));

    if (!myCurrentFileMode) {
      DefaultActionGroup groupBy = createGroupByActionGroup();
      toolbarGroup.add(groupBy);
    }

    toolbarGroup.add(new MyPreviewAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TODO_VIEW_TOOLBAR, toolbarGroup, false);
    toolbar.setTargetComponent(myTree);
    setToolbar(toolbar.getComponent());
  }

  protected @NotNull DefaultActionGroup createGroupByActionGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    return (DefaultActionGroup) actionManager.getAction("TodoViewGroupByGroup");
  }

  protected AnAction createAutoScrollToSourceAction() {
    return myAutoScrollToSourceHandler.createToggleAction();
  }

  protected JComponent createCenterComponent() {
    Splitter splitter = new OnePixelSplitter(false);
    splitter.setSecondComponent(myUsagePreviewPanel);
    JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), this, 1000);
    loadingPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    splitter.setFirstComponent(loadingPanel);
    return splitter;
  }

  @Override
  public void dispose() {}

  /**
   * Updates current filter. If previously set filter was removed then empty filter is set.
   *
   * @see TodoTreeBuilder#setTodoFilter
   */
  void updateTodoFilter() {
    TodoFilter filter = TodoConfiguration.getInstance().getTodoFilter(mySettings.todoFilterName);
    setTodoFilter(filter);
  }

  /**
   * Sets specified {@code TodoFilter}. The method also updates window's title.
   *
   * @see TodoTreeBuilder#setTodoFilter
   */
  private void setTodoFilter(TodoFilter filter) {
    // Clear name of current filter if it was removed from configuration.
    String filterName = filter != null ? filter.getName() : null;
    mySettings.todoFilterName = filterName;
    // Update filter
    myTodoTreeBuilder.setTodoFilter(filter);
    // Update content's title
    myContent.setDescription(filterName);
  }

  protected @Nullable PsiFile getSelectedFile() {
    Object object = TreeUtil.getLastUserObject(myTree.getSelectionPath());
    return object instanceof NodeDescriptor ? TodoTreeBuilder.getFileForNodeDescriptor((NodeDescriptor<?>)object) : null;
  }

  protected void setDisplayName(@NlsContexts.TabTitle  String tabName) {
    myContent.setDisplayName(tabName);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(TODO_PANEL_DATA_KEY, this);
    sink.set(PlatformCoreDataKeys.HELP_ID, "find.todoList");

    NodeDescriptor<?> selection = ObjectUtils.tryCast(TreeUtil.getLastUserObject(myTree.getSelectionPath()), NodeDescriptor.class);
    if (selection == null) return;
    sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> {
      PsiFile file = TodoTreeBuilder.getFileForNodeDescriptor(selection);
      return PsiUtilCore.getVirtualFile(file);
    });
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      PsiElement selectedElement = TodoTreeHelper.getInstance(myProject).getSelectedElement(selection);
      if (selectedElement != null) return selectedElement;
      return TodoTreeBuilder.getFileForNodeDescriptor(selection);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
      VirtualFile file = PsiUtilCore.getVirtualFile(TodoTreeBuilder.getFileForNodeDescriptor(selection));
      return file == null ? null : new VirtualFile[]{file};
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      Object element = selection.getElement();
      if (!(element instanceof TodoFileNode || element instanceof TodoItemNode)) { // allow user to use F4 only on files an TODOs
        return null;
      }
      TodoItemNode pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      SmartTodoItemPointer value = pointer == null ? null : pointer.getValue();
      return value == null ? null : PsiNavigationSupport.getInstance().createNavigatable(
        myProject,
        value.getTodoItem().getFile().getVirtualFile(),
        value.getRangeMarker().getStartOffset());
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myOccurenceNavigator.getActionUpdateThread();
  }

  @Override
  public @Nullable OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  @Override
  public @NotNull String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  @Override
  public @Nullable OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public @NotNull String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  protected void rebuildWithAlarm(final Alarm alarm) {
    alarm.cancelAllRequests();
    alarm.addRequest(() -> {
      myTodoTreeBuilder.rebuildCache();
    }, 300);
  }

  void updateVisibility(@NotNull ToolWindow toolWindow) {
    myTodoTreeBuilder.setUpdatable(toolWindow.isVisible() && myContent.isSelected());
  }

  /**
   * Provides support for "auto scroll to source" functionality
   */
  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    MyAutoScrollToSourceHandler() {
    }

    @Override
    protected boolean isAutoScrollMode() {
      return mySettings.isAutoScrollToSource;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {
      mySettings.isAutoScrollToSource = state;
    }
  }

  /**
   * Provides support for "Ctrl+Alt+Up/Down" navigation.
   */
  private final class MyOccurenceNavigator implements OccurenceNavigator {
    @Override
    public boolean hasNextOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (userObject == null) {
        return false;
      }
      if (userObject instanceof NodeDescriptor && ((NodeDescriptor<?>)userObject).getElement() instanceof TodoItemNode) {
        return myTree.getRowCount() != myTree.getRowForPath(path) + 1;
      }
      else {
        TreeModel model = myTree.getModel();
        return !model.isLeaf(node);
      }
    }

    @Override
    public boolean hasPreviousOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      return userObject instanceof NodeDescriptor && !isFirst(node);
    }

    private static boolean isFirst(final TreeNode node) {
      final TreeNode parent = node.getParent();
      return parent == null || parent.getIndex(node) == 0 && isFirst(parent);
    }

    @Override
    public @Nullable OccurenceInfo goNextOccurence() {
      return goToPointer(getNextPointer());
    }

    @Override
    public @Nullable OccurenceInfo goPreviousOccurence() {
      return goToPointer(getPreviousPointer());
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.todo");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.todo");
    }

    private @Nullable OccurenceInfo goToPointer(TodoItemNode pointer) {
      if (pointer == null) return null;
      myTodoTreeBuilder.select(pointer);
      return new OccurenceInfo(
        PsiNavigationSupport.getInstance()
                            .createNavigatable(myProject, pointer.getValue().getTodoItem().getFile().getVirtualFile(),
                                               pointer.getValue().getRangeMarker().getStartOffset()),
        -1,
        -1
      );
    }

    private @Nullable TodoItemNode getNextPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      Object element = ((NodeDescriptor<?>)userObject).getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getNextPointer((TodoItemNode)element);
      }
      else {
        pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      }
      return pointer;
    }

    private @Nullable TodoItemNode getPreviousPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      Object element = ((NodeDescriptor<?>)userObject).getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getPreviousPointer((TodoItemNode)element);
      }
      else {
        Object sibling = myTodoTreeBuilder.getPreviousSibling(element);
        if (sibling == null) {
          return null;
        }
        pointer = myTodoTreeBuilder.getLastPointerForElement(sibling);
      }
      return pointer;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  public static final class MyShowPackagesAction extends ToggleAction implements DumbAware {
    public MyShowPackagesAction() {
      super(IdeBundle.messagePointer("action.group.by.packages"), PlatformIcons.GROUP_BY_PACKAGES);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(e.getData(TODO_PANEL_DATA_KEY) != null);
      super.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      return todoPanel != null && todoPanel.mySettings.arePackagesShown;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      if (todoPanel != null) {
        todoPanel.mySettings.arePackagesShown = state;
        todoPanel.myTodoTreeBuilder.setShowPackages(state);
      }
    }
  }

  public static final class MyShowModulesAction extends ToggleAction implements DumbAware {
    public MyShowModulesAction() {
      super(IdeBundle.messagePointer("action.group.by.modules"), AllIcons.Actions.GroupByModule);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(e.getData(TODO_PANEL_DATA_KEY) != null);
      super.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      return todoPanel != null && todoPanel.mySettings.areModulesShown;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);

      if (todoPanel != null) {
        todoPanel.mySettings.areModulesShown = state;
        todoPanel.myTodoTreeBuilder.setShowModules(state);
      }
    }
  }

  public static final class MyFlattenPackagesAction extends ToggleAction implements DumbAware {
    public MyFlattenPackagesAction() {
      super(IdeBundle.messagePointer("action.flatten.view"), PlatformIcons.FLATTEN_PACKAGES_ICON);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("   " + getTemplateText());
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      e.getPresentation().setEnabled(todoPanel != null && todoPanel.mySettings.arePackagesShown);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      return todoPanel != null && todoPanel.mySettings.areFlattenPackages;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      TodoPanel todoPanel = e.getData(TODO_PANEL_DATA_KEY);
      if (todoPanel != null) {
        todoPanel.mySettings.areFlattenPackages = state;
        todoPanel.myTodoTreeBuilder.setFlattenPackages(state);
      }
    }
  }

  private final class MyPreviewAction extends ToggleAction implements DumbAware {

    MyPreviewAction() {
      super(IdeBundle.messagePointer("todo.panel.preview.source.action.text"), Presentation.NULL_STRING, AllIcons.Actions.PreviewDetails);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return mySettings.showPreview;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      mySettings.showPreview = state;
      myUsagePreviewPanel.setVisible(state);
      myCoroutineHelper.schedulePreviewPanelLayoutUpdate();
    }
  }
}