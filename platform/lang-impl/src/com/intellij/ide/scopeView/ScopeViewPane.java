// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.CompoundTreeStructureProvider;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.ShowModulesAction;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ChangeListScope;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.stripe.ErrorStripePainter;
import com.intellij.ui.stripe.TreeUpdater;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ArrayUtilRt.EMPTY_STRING_ARRAY;
import static com.intellij.util.concurrency.EdtExecutorService.getScheduledExecutorInstance;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class ScopeViewPane extends AbstractProjectViewPane {
  @NonNls public static final String ID = "Scope";
  private static final Logger LOG = Logger.getInstance(ScopeViewPane.class);
  private final NamedScopesHolder myDependencyValidationManager;
  private final NamedScopesHolder myNamedScopeManager;
  private final NamedScopesHolder.ScopeListener myScopeListener = new NamedScopesHolder.ScopeListener() {
    private final AtomicLong counter = new AtomicLong();

    @Override
    public void scopesChanged() {
      if (myProject.isDisposed()) return;
      long count = counter.incrementAndGet();
      getScheduledExecutorInstance().schedule(() -> {
        // is this request still actual after 10 ms?
        if (count == counter.get()) {
          ProjectView view = myProject.isDisposed() ? null : ProjectView.getInstance(myProject);
          if (view == null) return;
          myFilters = map(myDependencyValidationManager, myNamedScopeManager);
          String currentId = view.getCurrentViewId();
          String currentSubId = getSubId();
          // update changes subIds if needed
          view.removeProjectPane(ScopeViewPane.this);
          view.addProjectPane(ScopeViewPane.this);
          if (currentId == null) return;
          if (currentId.equals(getId())) {
            // try to restore selected subId
            view.changeView(currentId, currentSubId);
          }
          else {
            view.changeView(currentId);
          }
        }
      }, 10, MILLISECONDS);
    }
  };
  private final ScopeViewTreeModel myTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private LinkedHashMap<String, NamedScopeFilter> myFilters;
  private JScrollPane myScrollPane;

  public ScopeViewPane(@NotNull Project project, @NotNull DependencyValidationManager dvm, @NotNull NamedScopeManager nsm) {
    super(project);
    myDependencyValidationManager = dvm;
    myNamedScopeManager = nsm;
    myFilters = map(myDependencyValidationManager, myNamedScopeManager);
    myTreeModel = new ScopeViewTreeModel(project, new ProjectViewSettings.Delegate(project, ID));
    myTreeModel.setStructureProvider(CompoundTreeStructureProvider.get(project));
    myTreeModel.setNodeDecorator(CompoundProjectViewNodeDecorator.get(project));
    myAsyncTreeModel = new AsyncTreeModel(myTreeModel, true, this);
    myDependencyValidationManager.addScopeListener(myScopeListener);
    myNamedScopeManager.addScopeListener(myScopeListener);
    ChangeListManager.getInstance(project).addChangeListListener(new ChangeListAdapter() {
      @Override
      public void changeListAdded(ChangeList list) {
        myDependencyValidationManager.fireScopeListeners();
      }

      @Override
      public void changeListRemoved(ChangeList list) {
        myDependencyValidationManager.fireScopeListeners();
      }

      @Override
      public void changeListRenamed(ChangeList list, String name) {
        myDependencyValidationManager.fireScopeListeners();
      }

      @Override
      public void changeListsChanged() {
        NamedScopeFilter filter = myTreeModel.getFilter();
        if (filter != null && filter.getScope() instanceof ChangeListScope) {
          myTreeModel.setFilter(filter);
        }
      }
    }, this);
    installComparator();
  }

  @Override
  public void dispose() {
    JTree tree = myTree;
    if (tree != null) tree.setModel(null);
    myDependencyValidationManager.removeScopeListener(myScopeListener);
    myNamedScopeManager.removeScopeListener(myScopeListener);
    super.dispose();
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public int getWeight() {
    return 4;
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  @Override
  public JComponent createComponent() {
    onSubIdChange();
    if (myTree == null) {
      myTree = new ProjectViewTree(myAsyncTreeModel);
      myTree.setName("ScopeViewTree");
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      myTree.addTreeSelectionListener(new RestoreSelectionListener());
      TreeUtil.installActions(myTree);
      ToolTipManager.sharedInstance().registerComponent(myTree);
      EditSourceOnDoubleClickHandler.install(myTree);
      CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionPlaces.SCOPE_VIEW_POPUP);
      new TreeSpeedSearch(myTree);
      enableDnD();
    }
    if (myScrollPane == null) {
      myScrollPane = createScrollPane(myTree, true);
      ErrorStripePainter painter = new ErrorStripePainter(true);
      Disposer.register(this, new TreeUpdater<ErrorStripePainter>(painter, myScrollPane, myTree) {
        @Override
        protected void update(ErrorStripePainter painter, int index, Object object) {
          super.update(painter, index, myTreeModel.getStripe(object, myTree.isExpanded(index)));
        }
      });
    }
    return myScrollPane;
  }

  @NotNull
  @Override
  public ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    saveExpandedPaths();
    myTreeModel.invalidate(null);
    restoreExpandedPaths(); // TODO:check
    return ActionCallback.DONE;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  @Override
  public void select(Object object, VirtualFile file, boolean requestFocus) {
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    NamedScopeFilter current = myTreeModel.getFilter();
    if (select(element, file, requestFocus, current)) return;
    for (NamedScopeFilter filter : getFilters()) {
      if (current != filter && select(element, file, requestFocus, filter)) return;
    }
  }

  private boolean select(PsiElement element, VirtualFile file, boolean requestFocus, NamedScopeFilter filter) {
    if (filter == null || !filter.accept(file)) return false;
    String subId = filter.toString();
    if (!Objects.equals(subId, getSubId())) {
      if (!requestFocus) return true;
      ProjectView.getInstance(myProject).changeView(getId(), subId);
    }
    LOG.debug("select element: ", element, " in file: ", file);
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file);
    if (visitor == null) return true;
    JTree tree = myTree;
    TreeState.expand(tree, promise -> TreeUtil.visit(tree, visitor, path -> {
      if (selectPath(tree, path) || element == null || Registry.is("async.project.view.support.extra.select.disabled")) {
        promise.setResult(null);
      }
      else {
        // try to search the specified file instead of element,
        // because Kotlin files cannot represent containing functions
        TreeUtil.visit(tree, AbstractProjectViewPane.createVisitor(file), path2 -> {
          selectPath(tree, path2);
          promise.setResult(null);
        });
      }
    }));
    return true;
  }

  private static boolean selectPath(@NotNull JTree tree, TreePath path) {
    if (path == null) return false;
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPath(tree, path); // select and scroll to center
    return true;
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    /*
    final ActionCallback callback = myViewPanel.getActionCallback();
    return callback == null ? ActionCallback.DONE : callback;
    */
    // TODO: only initial expand
    return ActionCallback.DONE;
  }

  @Override
  protected void onSubIdChange() {
    myTreeModel.setFilter(getFilter(getSubId()));
  }

  @NotNull
  @Override
  public String[] getSubIds() {
    LinkedHashMap<String, NamedScopeFilter> map = myFilters;
    if (map == null || map.isEmpty()) return EMPTY_STRING_ARRAY;
    return ContainerUtil.toArray(map.keySet(), EMPTY_STRING_ARRAY);
  }

  @NotNull
  @Override
  public String getPresentableSubIdName(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter != null ? filter.getScope().getName() : getTitle();
  }

  @Override
  public Icon getPresentableSubIdIcon(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter != null ? filter.getScope().getIcon() : getIcon();
  }

  @Override
  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.add(ActionManager.getInstance().getAction("ScopeView.EditScopes"));
    actionGroup.addAction(new ShowModulesAction(myProject) {
      @NotNull
      @Override
      protected String getId() {
        return ID;
      }
    }).setAsSecondary(true);
    actionGroup.addAction(createFlattenModulesAction(() -> true)).setAsSecondary(true);
  }

  @Override
  protected void installComparator(AbstractTreeBuilder builder, Comparator<NodeDescriptor> comparator) {
    myTreeModel.setComparator(comparator);
  }

  @Nullable
  @Override
  public Object getElementFromTreeNode(@Nullable Object node) {
    return myTreeModel.getPsiElement(node);
  }

  @Override
  public Object getData(final String dataId) {
    Object data = super.getData(dataId);
    if (data != null) return data;
    //TODO:myViewPanel == null ? null : myViewPanel.getData(dataId);
    return null;
  }

  @NotNull
  Iterable<NamedScopeFilter> getFilters() {
    return myFilters.values();
  }

  @Nullable
  NamedScopeFilter getFilter(@Nullable String subId) {
    LinkedHashMap<String, NamedScopeFilter> map = myFilters;
    return map == null || subId == null ? null : map.get(subId);
  }

  @NotNull
  private static LinkedHashMap<String, NamedScopeFilter> map(NamedScopesHolder... holders) {
    LinkedHashMap<String, NamedScopeFilter> map = new LinkedHashMap<>();
    for (NamedScopeFilter filter : NamedScopeFilter.list(holders)) {
      NamedScopeFilter old = map.put(filter.toString(), filter);
      if (old != null) LOG.warn("DUPLICATED: " + filter);
    }
    return map;
  }
}
