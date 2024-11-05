// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.platform.execution.serviceView.ServiceViewNavBarService.ServiceViewNavBarSelector;
import com.intellij.platform.navbar.frontend.vm.NavBarVm;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;

import static com.intellij.platform.execution.serviceView.ServiceViewDragHelper.getTheOnlyRootContributor;

final class ServiceTreeView extends ServiceView {
  private static final String ADD_SERVICE_ACTION_ID = "ServiceView.AddService";

  private final ServiceViewTree myTree;
  private final ServiceViewTreeModel myTreeModel;
  private final ServiceViewModel.ServiceViewModelListener myListener;

  private final ServiceViewNavBarPanel myNavBarPanel;

  private volatile ServiceViewItem myLastSelection;
  private boolean mySelected;
  private volatile Promise<?> myUpdateSelectionPromise;

  ServiceTreeView(@NotNull Project project, @NotNull ServiceViewModel model, @NotNull ServiceViewUi ui, @NotNull ServiceViewState state) {
    super(new BorderLayout(), project, model, ui);

    myTreeModel = new ServiceViewTreeModel(model);
    myTree = new ServiceViewTree(myTreeModel, this);

    ServiceViewActionProvider actionProvider = ServiceViewActionProvider.getInstance();
    ui.setServiceToolbar(actionProvider);
    ui.setMasterComponent(myTree, actionProvider);

    myTree.setDragEnabled(true);
    DnDManager.getInstance().registerSource(ServiceViewDragHelper.createSource(this), myTree);
    DnDManager.getInstance().registerTarget(ServiceViewDragHelper.createTarget(myTree), myTree);

    add(myUi.getComponent(), BorderLayout.CENTER);

    myTree.addTreeSelectionListener(new RestoreSelectionListener());
    myTree.addTreeSelectionListener(e -> onSelectionChanged());

    ServiceViewNavBarSelector selector = new ServiceViewNavBarSelector() {
      @Override
      public void select(@NotNull ServiceModel.ServiceViewItem item) {
        ServiceTreeView.this.select(item.getValue(), item.getRootContributor().getClass())
          .onSuccess(result -> AppUIExecutor.onUiThread().expireWith(ServiceTreeView.this).submit(() -> {
            JComponent component = getUi().getDetailsComponent();
            if (component != null) {
              IdeFocusManager.getInstance(getProject()).requestFocus(component, false);
            }
          }));
      }

      @Nullable
      @Override
      public ServiceViewItem getSelectedItem() {
        return myLastSelection;
      }
    };
    myNavBarPanel = ServiceViewNavBarService.getInstance(project).createNavBarPanel(this, selector);
    myUi.setNavBar(myNavBarPanel);

    myListener = new ServiceViewTreeModelListener();
    model.addModelListener(myListener);

    if (model instanceof ServiceViewModel.AllServicesModel) {
      setEmptyText(myTree, myTree.getEmptyText());
    }

    if (state.expandedPaths.isEmpty()) {
      state.treeState.applyTo(myTree, myTreeModel.getRoot());
    }
    else {
      Set<ServiceViewItem> roots = new HashSet<>(model.getVisibleRoots());
      List<TreePath> adjusted = adjustPaths(state.expandedPaths, roots, myTreeModel.getRoot());
      if (!adjusted.isEmpty()) {
        TreeUtil.promiseExpand(myTree, new PathExpandVisitor(adjusted));
      }
    }
  }

  @Override
  public void dispose() {
    getModel().removeModelListener(myListener);
    super.dispose();
  }

  @Override
  void saveState(@NotNull ServiceViewState state) {
    super.saveState(state);
    myUi.saveState(state);
    state.treeState = TreeState.createOn(myTree);
    state.expandedPaths = TreeUtil.collectExpandedPaths(myTree);
  }

  @NotNull
  @Override
  List<ServiceViewItem> getSelectedItems() {
    int[] rows = myTree.getSelectionRows();
    if (rows == null || rows.length == 0) return Collections.emptyList();

    List<Object> objects = TreeUtil.collectSelectedUserObjects(myTree);
    if (objects.size() != rows.length) {
      return ContainerUtil.mapNotNull(objects, o -> ObjectUtils.tryCast(o, ServiceViewItem.class));
    }

    List<Pair<Object, Integer>> objectRows = new ArrayList<>();
    for (int i = 0; i < rows.length; i++) {
      objectRows.add(Pair.create(objects.get(i), rows[i]));
    }
    objectRows.sort(Pair.comparingBySecond());
    return ContainerUtil.mapNotNull(objectRows, pair -> ObjectUtils.tryCast(pair.first, ServiceViewItem.class));
  }

  @Override
  Promise<Void> select(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return doSelect(service, contributorClass, false);
  }

  private Promise<Void> selectSafe(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return doSelect(service, contributorClass, true);
  }

  private Promise<Void> doSelect(@NotNull Object service, @NotNull Class<?> contributorClass, boolean safe) {
    ServiceViewItem selectedItem = myLastSelection;
    if (selectedItem == null || !selectedItem.getValue().equals(service)) {
      AsyncPromise<Void> result = new AsyncPromise<>();
      Promise<TreePath> pathPromise =
        safe ? myTreeModel.findPathSafe(service, contributorClass) : myTreeModel.findPath(service, contributorClass);
      pathPromise
        .onError(result::setError)
        .onSuccess(path -> {
          TreeUtil.promiseSelect(myTree, new PathSelectionVisitor(path))
            .onError(result::setError)
            .onSuccess(selectedPath -> {
              result.setResult(null);
              cancelSelectionUpdate();
            });
          cancelSelectionUpdate();
        });
      return result;
    }
    return Promises.resolvedPromise();
  }

  @Override
  Promise<Void> expand(@NotNull Object service, @NotNull Class<?> contributorClass) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    myTreeModel.findPath(service, contributorClass)
      .onError(result::setError)
      .onSuccess(path -> {
        TreeUtil.promiseExpand(myTree, new PathSelectionVisitor(path))
          .onError(result::setError)
          .onSuccess(expandedPath -> {
            result.setResult(null);
          });
      });
    return result;
  }

  @Override
  Promise<Void> extract(@NotNull Object service, @NotNull Class<?> contributorClass) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    myTreeModel.findPath(service, contributorClass)
      .onError(result::setError)
      .onSuccess(path -> {
        ServiceViewItem item = (ServiceViewItem)path.getLastPathComponent();
        if (item instanceof ServiceModel.ServiceNode node && node.isLoaded() && !node.isChildrenInitialized()) {
          // Initialize children on BGT before extract in order correctly determine whether it leaf or not and
          // use appropriate ServiceViewUi.
          node.getChildren();
        }
        AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
          ServiceViewManagerImpl manager = (ServiceViewManagerImpl)ServiceViewManager.getInstance(getProject());
          manager.extract(new ServiceViewDragHelper.ServiceViewDragBean(this, Collections.singletonList(item)));
          result.setResult(null);
        });
      });
    return result;
  }

  @Override
  void onViewSelected() {
    mySelected = true;
    if (myLastSelection != null) {
      ServiceViewDescriptor descriptor = myLastSelection.getViewDescriptor();
      onViewSelected(descriptor);
      myUi.setDetailsComponentVisible(descriptor.isContentPartVisible());
      myUi.setDetailsComponent(descriptor.getContentComponent());
    }
    else {
      myUi.setDetailsComponent(null);
    }
  }

  @Override
  void onViewUnselected() {
    mySelected = false;
    if (myLastSelection != null) {
      myLastSelection.getViewDescriptor().onNodeUnselected();
    }
  }

  @Override
  void jumpToServices() {
    if (myTree.isShowing()) {
      IdeFocusManager.getInstance(getProject()).requestFocus(myTree, false);
    }
    else {
      NavBarVm vm = myNavBarPanel.getModel();
      if (vm != null) {
        myNavBarPanel.updateModel();
        vm.selectTail(false);
      }
    }
  }

  private void onSelectionChanged() {
    List<ServiceViewItem> selected = getSelectedItems();

    ServiceViewItem newSelection;
    ServiceViewDescriptor newDescriptor;
    if (selected.size() == 1) {
      newSelection = selected.get(0);
      newDescriptor = newSelection.getViewDescriptor();
    }
    else {
      newSelection = null;
      ServiceViewContributor<?> contributor = getTheOnlyRootContributor(selected);
      newDescriptor = contributor == null ? null : contributor.getViewDescriptor(getProject());
    }


    if (newSelection != null && newSelection.equals(myLastSelection)) return;

    ServiceViewDescriptor oldDescriptor = myLastSelection == null ? null : myLastSelection.getViewDescriptor();
    if (Comparing.equal(newDescriptor, oldDescriptor)) return;

    if (oldDescriptor != null && mySelected) {
      oldDescriptor.onNodeUnselected();
    }

    myLastSelection = newSelection;
    myNavBarPanel.updateModel();

    if (!mySelected) return;

    if (newDescriptor != null) {
      newDescriptor.onNodeSelected(ContainerUtil.map(selected, ServiceViewItem::getValue));
    }
    if (newDescriptor != null) {
      myUi.setDetailsComponentVisible(newDescriptor.isContentPartVisible());
    }
    myUi.setDetailsComponent(newDescriptor == null ? null : newDescriptor.getContentComponent());
  }

  private void selectFirstItemIfNeeded() {
    AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
      List<ServiceViewItem> selected = getSelectedItems();
      if (selected.isEmpty()) {
        ServiceViewItem item = ContainerUtil.getFirstItem(getModel().getRoots());
        if (item != null) {
          select(item.getValue(), item.getRootContributor().getClass());
        }
      }
    });
  }

  private void updateLastSelection() {
    ServiceViewItem lastSelection = myLastSelection;
    WeakReference<ServiceViewItem> itemRef =
      new WeakReference<>(lastSelection == null ? null : getModel().findItemSafe(lastSelection));
    AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
      List<ServiceViewItem> selected = getSelectedItems();
      if (selected.isEmpty()) {
        ServiceViewItem item = ContainerUtil.getFirstItem(getModel().getRoots());
        if (item != null) {
          selectSafe(item.getValue(), item.getRootContributor().getClass());
          return;
        }
      }

      ServiceViewItem updatedItem = itemRef.get();
      ServiceViewItem newSelection = ContainerUtil.getOnlyItem(selected);
      if (Comparing.equal(newSelection, updatedItem)) {
        newSelection = updatedItem;
      }
      if (Comparing.equal(newSelection, myLastSelection)) {
        myLastSelection = newSelection;
        // Skip updating details component if updatedItem has been already marked as removed,
        // thus details component will be updated in the next already submitted update runnable.
        if (mySelected && (updatedItem == null || !updatedItem.isRemoved())) {
          ServiceViewDescriptor descriptor = newSelection == null || (newSelection.isRemoved() && updatedItem == null) ?
                                             null : newSelection.getViewDescriptor();
          if (descriptor != null) {
            myUi.setDetailsComponentVisible(descriptor.isContentPartVisible());
          }
          myUi.setDetailsComponent(descriptor == null ? null : descriptor.getContentComponent());
        }
      }
    });
  }

  private void updateSelectionPaths() {
    AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
      TreePath[] currentPaths = myTree.getSelectionPaths();
      List<TreePath> selectedPaths =
        currentPaths == null || currentPaths.length == 0 ? Collections.emptyList() : Arrays.asList(currentPaths);
      myTreeModel.rootsChanged();
      if (selectedPaths.isEmpty()) return;

      myTreeModel.getInvoker().invokeLater(() -> {
        List<Promise<TreePath>> pathPromises =
          ContainerUtil.mapNotNull(selectedPaths, path -> {
            ServiceViewItem item = ObjectUtils.tryCast(path.getLastPathComponent(), ServiceViewItem.class);
            return item == null ? null : myTreeModel.findPathSafe(item.getValue(), item.getRootContributor().getClass());
          });
        Promises.collectResults(pathPromises, true).onProcessed(paths -> {
          if (paths != null && !paths.isEmpty()) {
            if (!paths.equals(selectedPaths)) {
              Promise<?> newSelectPromise = TreeUtil.promiseSelect(myTree, paths.stream().map(PathSelectionVisitor::new));
              cancelSelectionUpdate();
              if (newSelectPromise instanceof AsyncPromise) {
                ((AsyncPromise<?>)newSelectPromise).onError(t -> {
                  if (t instanceof CancellationException) {
                    TreeUtil.promiseExpand(myTree, paths.stream().map(path -> new PathSelectionVisitor(path.getParentPath())));
                  }
                });
              }
              myUpdateSelectionPromise = newSelectPromise;
            }
            else {
              AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
                TreePath[] selectionPaths = myTree.getSelectionPaths();
                if (selectionPaths != null && isSelectionUpdateNeeded(new SmartList<>(selectionPaths), paths)) {
                  myTree.setSelectionPaths(paths.toArray(new TreePath[0]));
                }
              });
            }
          }
        });
      });
    });
  }

  /**
   * @return {@code true} if selection and updated paths are equal but contain at least one nonidentical element, otherwise {@code false}
   */
  private static boolean isSelectionUpdateNeeded(List<? extends TreePath> selectionPaths, List<? extends TreePath> updatedPaths) {
    if (selectionPaths.size() != updatedPaths.size()) return false;

    boolean result = false;
    for (int i = 0; i < selectionPaths.size(); i++) {
      TreePath selectionPath = selectionPaths.get(i);
      TreePath updatedPath = updatedPaths.get(i);
      do {
        if (updatedPath == null) return false;

        Object selectedComponent = selectionPath.getLastPathComponent();
        Object updatedComponent = updatedPath.getLastPathComponent();
        if (selectedComponent != updatedComponent) {
          if (!selectedComponent.equals(updatedComponent)) return false;

          result = true;
        }
        selectionPath = selectionPath.getParentPath();
        updatedPath = updatedPath.getParentPath();
      }
      while (selectionPath != null);

      if (updatedPath != null) return false;
    }
    return result;
  }

  @Override
  void setAutoScrollToSourceHandler(@NotNull AutoScrollToSourceHandler autoScrollToSourceHandler) {
    super.setAutoScrollToSourceHandler(autoScrollToSourceHandler);
    autoScrollToSourceHandler.install(myTree);
  }

  @Override
  List<Object> getChildrenSafe(@NotNull List<Object> valueSubPath, @NotNull Class<?> contributorClass) {
    Queue<Object> values = new LinkedList<>(valueSubPath);
    Object visibleRoot = values.poll();
    if (visibleRoot == null) return Collections.emptyList();

    List<? extends ServiceViewItem> roots = getModel().getVisibleRoots();
    ServiceViewItem item = JBTreeTraverser.from((Function<ServiceViewItem, List<ServiceViewItem>>)node ->
      contributorClass.isInstance(node.getRootContributor()) ? new ArrayList<>(getModel().getChildren(node)) : null)
      .withRoots(roots)
      .traverse(ServiceModel.ONLY_LOADED_BFS)
      .filter(node -> node.getValue().equals(visibleRoot))
      .first();
    if (item == null) return Collections.emptyList();

    while (!values.isEmpty()) {
      Object value = values.poll();
      item = ContainerUtil.find(getModel().getChildren(item), child -> value.equals(child.getValue()));
      if (item == null) return Collections.emptyList();
    }
    return ContainerUtil.map(getModel().getChildren(item), ServiceViewItem::getValue);
  }

  private void cancelSelectionUpdate() {
    Promise<?> selectPromise = myUpdateSelectionPromise;
    if (selectPromise instanceof AsyncPromise) {
      ((AsyncPromise<?>)selectPromise).cancel();
    }
  }

  private static void setEmptyText(JComponent component, StatusText emptyText) {
    emptyText.setText(ExecutionBundle.message("service.view.empty.tree.text"));
    emptyText.appendSecondaryText(ExecutionBundle.message("service.view.add.service.action.name"),
                                  SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                  new ActionListener() {
                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                      ActionGroup addActionGroup = ObjectUtils.tryCast(
                                        ActionManager.getInstance().getAction(ADD_SERVICE_ACTION_ID), ActionGroup.class);
                                      if (addActionGroup == null) return;

                                      Point position = component.getMousePosition();
                                      if (position == null) {
                                        Rectangle componentBounds = component.getBounds();
                                        Rectangle textBounds = emptyText.getComponent().getBounds();
                                        position = new Point(componentBounds.width / 2,
                                                             componentBounds.height / (emptyText.isShowAboveCenter() ? 3 : 2) +
                                                             textBounds.height / 4);

                                      }
                                      DataContext dataContext = DataManager.getInstance().getDataContext(component);
                                      JBPopupFactory.getInstance().createActionGroupPopup(
                                        addActionGroup.getTemplatePresentation().getText(), addActionGroup, dataContext,
                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                        false, null, -1, null, ActionPlaces.getActionGroupPopupPlace(ADD_SERVICE_ACTION_ID))
                                        .show(new RelativePoint(component, position));
                                    }
                                  });
    AnAction addAction = ActionManager.getInstance().getAction(ADD_SERVICE_ACTION_ID);
    ShortcutSet shortcutSet = addAction == null ? null : addAction.getShortcutSet();
    Shortcut shortcut = shortcutSet == null ? null : ArrayUtil.getFirstElement(shortcutSet.getShortcuts());
    if (shortcut != null) {
      emptyText.appendSecondaryText(" (" + KeymapUtil.getShortcutText(shortcut) + ")", StatusText.DEFAULT_ATTRIBUTES, null);
    }
  }

  private static List<TreePath> adjustPaths(List<? extends TreePath> paths, Collection<? extends ServiceViewItem> roots, Object treeRoot) {
    List<TreePath> result = new SmartList<>();
    for (TreePath path : paths) {
      Object[] items = path.getPath();
      for (int i = 1; i < items.length; i++) {
        if (roots.contains(items[i])) {
          Object[] adjustedItems = ArrayUtil.insert(items, 0, treeRoot);
          result.add(new TreePath(adjustedItems));
          break;
        }
      }
    }
    return result;
  }

  private final class ServiceViewTreeModelListener implements ServiceViewModel.ServiceViewModelListener {
    @Override
    public void eventProcessed(ServiceEventListener.@NotNull ServiceEvent e) {
      if (e.type == ServiceEventListener.EventType.UNLOAD_SYNC_RESET) {
        AppUIExecutor.onUiThread().expireWith(ServiceTreeView.this).submit(() -> {
          resetTreeModel();
          updateNavBar();
        });
        updateLastSelection();
      }
      else {
        updateNavBar();
        ServiceViewItem lastSelection = myLastSelection;
        if (lastSelection != null && lastSelection.getRootContributor().getClass().equals(e.contributorClass)) {
          updateLastSelection();
        }
        else {
          selectFirstItemIfNeeded();
        }
      }
      updateSelectionPaths();
    }

    @Override
    public void structureChanged() {
      selectFirstItemIfNeeded();
      updateSelectionPaths();
    }

    private void resetTreeModel() {
      TreeModel model = myTree.getModel();
      if (model instanceof Disposable) {
        Disposer.dispose((Disposable)model);
      }
      myTree.setModel(null);
      AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTreeModel, ServiceTreeView.this);
      myTree.setModel(asyncTreeModel);
    }

    private void updateNavBar() {
      myNavBarPanel.updateModel();
    }
  }

  private static final class PathSelectionVisitor implements TreeVisitor {
    private final Queue<Object> myPath;

    PathSelectionVisitor(TreePath path) {
      myPath = ContainerUtil.newLinkedList(path.getPath());
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      Object node = path.getLastPathComponent();
      if (node.equals(myPath.peek())) {
        myPath.poll();
        return myPath.isEmpty() ? Action.INTERRUPT : Action.CONTINUE;
      }
      return Action.SKIP_CHILDREN;
    }
  }

  private static final class PathExpandVisitor implements TreeVisitor {
    private final List<? extends TreePath> myPaths;

    PathExpandVisitor(List<? extends TreePath> paths) {
      myPaths = paths;
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      if (path.getParentPath() == null) return Action.CONTINUE;

      for (TreePath treePath : myPaths) {
        if (treePath.equals(path)) {
          myPaths.remove(treePath);
          return myPaths.isEmpty() ? Action.INTERRUPT : Action.CONTINUE;
        }
      }
      return Action.SKIP_CHILDREN;
    }
  }
}
