// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.stripe.ErrorStripePainter;
import com.intellij.ui.stripe.TreeUpdater;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.module.ModuleGrouperKt.isQualifiedModuleNamesEnabled;
import static com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES;
import static com.intellij.util.ArrayUtilRt.EMPTY_STRING_ARRAY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class ScopeViewPane extends AbstractProjectViewPane {
  @NonNls public static final String ID = "Scope";
  private static final Logger LOG = Logger.getInstance(ScopeViewPane.class);
  private final IdeView myIdeView = new IdeViewForProjectViewPane(() -> this);
  private final NamedScopesHolder myDependencyValidationManager;
  private final NamedScopesHolder myNamedScopeManager;
  private ScopeViewTreeModel myTreeModel;
  private final AtomicReference<Map<String, NamedScopeFilter>> myFilters = new AtomicReference<>();
  private JScrollPane myScrollPane;

  private static Project checkApplicability(@NotNull Project project) {
    // TODO: make a proper extension point here
    if (PlatformUtils.isPyCharmEducational() || PlatformUtils.isRider()) {
      throw ExtensionNotApplicableException.create();
    }
    return project;
  }

  public ScopeViewPane(@NotNull Project project) {
    super(checkApplicability(project));

    myDependencyValidationManager = DependencyValidationManager.getInstance(project);
    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myFilters.set(map(myDependencyValidationManager, myNamedScopeManager));

    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      private final AtomicLong counter = new AtomicLong();

      @Override
      public void scopesChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        long count = counter.incrementAndGet();
        EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
          // is this request still actual after 10 ms?
          if (count != counter.get()) {
            return;
          }

          ProjectView view = myProject.isDisposed() ? null : ProjectView.getInstance(myProject);
          if (view == null) {
            return;
          }
          myFilters.set(map(myDependencyValidationManager, myNamedScopeManager));
          String currentId = view.getCurrentViewId();
          String currentSubId = getSubId();
          // update changes subIds if needed
          view.removeProjectPane(ScopeViewPane.this);
          view.addProjectPane(ScopeViewPane.this);
          if (currentId == null) {
            return;
          }
          if (currentId.equals(getId())) {
            // try to restore selected subId
            view.changeView(currentId, currentSubId);
          }
          else {
            view.changeView(currentId);
          }
        }, 10, MILLISECONDS);
      }
    };

    myDependencyValidationManager.addScopeListener(scopeListener, this);
    myNamedScopeManager.addScopeListener(scopeListener, this);
  }

  @Override
  public void dispose() {
    JTree tree = myTree;
    if (tree != null) {
      tree.setModel(null);
    }
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

  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  @Override
  public boolean isFileNestingEnabled() {
    return true;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    if (myTreeModel == null) {
      myTreeModel = new ScopeViewTreeModel(myProject, new ProjectViewSettings.Delegate(myProject, ID));
      myTreeModel.setStructureProvider(CompoundTreeStructureProvider.get(myProject));
      myTreeModel.setNodeDecorator(CompoundProjectViewNodeDecorator.get(myProject));
      myTreeModel.setFilter(getFilter(getSubId()));
      myTreeModel.setComparator(createComparator());
    }

    if (myTree == null) {
      myTree = new ProjectViewTree(new AsyncTreeModel(myTreeModel, this));
      myTree.setName("ScopeViewTree");
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      myTree.addTreeSelectionListener(new RestoreSelectionListener());
      TreeUtil.installActions(myTree);
      ToolTipManager.sharedInstance().registerComponent(myTree);
      EditSourceOnDoubleClickHandler.install(myTree);
      EditSourceOnEnterKeyHandler.install(myTree);
      CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionPlaces.SCOPE_VIEW_POPUP);
      TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);
      enableDnD();
      myTree.getEmptyText()
        .setText(IdeBundle.message("scope.view.empty.text"))
        .appendSecondaryText(IdeBundle.message("scope.view.empty.link"), LINK_PLAIN_ATTRIBUTES, event -> {
          ProjectView view = myProject.isDisposed() ? null : ProjectView.getInstance(myProject);
          if (view != null) view.changeView(ProjectViewPane.ID);
        });
    }
    if (myScrollPane == null) {
      myScrollPane = ScrollPaneFactory.createScrollPane(myTree, true);
      ErrorStripePainter painter = new ErrorStripePainter(true);
      Disposer.register(this, new TreeUpdater<>(painter, myScrollPane, myTree) {
        @Override
        protected void update(ErrorStripePainter painter, int index, Object object) {
          super.update(painter, index, myTreeModel.getStripe(object, myTree.isExpanded(index)));
        }
      });
    } else {
      SwingUtilities.updateComponentTreeUI(myScrollPane);
    }
    return myScrollPane;
  }

  @Override
  public @NotNull ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    if (myTreeModel == null) {
      // not initialized yet
      return ActionCallback.REJECTED;
    }

    saveExpandedPaths();
    myTreeModel.invalidate(null);
    restoreExpandedPaths(); // TODO:check
    return ActionCallback.DONE;
  }

  @NotNull
  @Override
  public SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  @Override
  public void select(Object object, VirtualFile file, boolean requestFocus) {
    if (myTreeModel == null) {
      // not initialized yet
      return;
    }

    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    NamedScopeFilter current = myTreeModel.getFilter();
    if (select(element, file, requestFocus, current)) return;
    for (NamedScopeFilter filter : getFilters()) {
      if (current != filter && select(element, file, requestFocus, filter)) {
        return;
      }
    }
  }

  private void selectScopeView(String subId) {
    ProjectView view = myProject.isDisposed() ? null : ProjectView.getInstance(myProject);
    if (view != null) {
      view.changeView(getId(), subId);
    }
  }

  private boolean select(PsiElement element, VirtualFile file, boolean requestFocus, VirtualFileFilter filter) {
    if (filter == null || !filter.accept(file)) {
      return false;
    }

    String subId = filter.toString();
    if (!Objects.equals(subId, getSubId())) {
      if (!requestFocus) return true;
      selectScopeView(subId);
    }
    LOG.debug("select element: ", element, " in file: ", file);
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file);
    if (visitor == null) return true;
    JTree tree = myTree;
    myTreeModel.getUpdater().updateImmediately(() -> TreeState.expand(tree, promise -> TreeUtil.visit(tree, visitor, path -> {
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
    })));
    return true;
  }

  private static boolean selectPath(@NotNull JTree tree, TreePath path) {
    if (path == null) {
      return false;
    }
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
    updateSelectedScope();
  }

  @Override
  @CalledInAny
  public String @NotNull [] getSubIds() {
    Map<String, NamedScopeFilter> map = myFilters.get();
    if (map == null || map.isEmpty()) {
      return EMPTY_STRING_ARRAY;
    }
    return ArrayUtilRt.toStringArray(map.keySet());
  }

  @NotNull
  @Override
  public String getPresentableSubIdName(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter == null ? getTitle() : filter.getScope().getPresentableName();
  }

  @NotNull
  @Override
  public Icon getPresentableSubIdIcon(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter != null ? filter.getScope().getIcon() : getIcon();
  }

  @Nullable
  @Override
  public Object getValueFromNode(@Nullable Object node) {
    if (myTreeModel == null) {
      // not initialized yet
      return null;
    }
    return myTreeModel.getContent(node);
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object data = super.getData(dataId);
    if (data != null) {
      return data;
    }
    //TODO:myViewPanel == null ? null : myViewPanel.getData(dataId);
    if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
    }
    return null;
  }

  public void updateSelectedScope() {
    // not initialized yet
    if (myTreeModel == null) {
      return;
    }
    myTreeModel.setFilter(getFilter(getSubId()));
  }

  @Nullable
  public NamedScope getSelectedScope() {
    NamedScopeFilter filter = getFilter(getSubId());
    return filter == null ? null : filter.getScope();
  }

  @CalledInAny
  @NotNull
  Iterable<NamedScopeFilter> getFilters() {
    Map<String, NamedScopeFilter> map = myFilters.get();
    return map == null ? Collections.emptyList() : map.values();
  }

  @CalledInAny
  @Nullable
  NamedScopeFilter getFilter(@Nullable String subId) {
    Map<String, NamedScopeFilter> map = myFilters.get();
    return map == null || subId == null ? null : map.get(subId);
  }

  private static @NotNull Map<String, NamedScopeFilter> map(NamedScopesHolder... holders) {
    LinkedHashMap<String, NamedScopeFilter> map = new LinkedHashMap<>();
    for (NamedScopeFilter filter : NamedScopeFilter.list(holders)) {
      NamedScopeFilter old = map.put(filter.toString(), filter);
      if (old != null) {
        LOG.warn("DUPLICATED: " + filter);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean supportsAbbreviatePackageNames() {
    return false;
  }

  @Override
  public boolean supportsCompactDirectories() {
    return true;
  }

  @Override
  public boolean supportsFlattenModules() {
    return PlatformUtils.isIntelliJ() && isQualifiedModuleNamesEnabled(myProject) && ProjectView.getInstance(myProject).isShowModules(ID);
  }

  @Override
  public boolean supportsHideEmptyMiddlePackages() {
    return ProjectView.getInstance(myProject).isFlattenPackages(ID);
  }

  @Override
  public boolean supportsShowExcludedFiles() {
    return true;
  }

  @Override
  public boolean supportsShowModules() {
    return PlatformUtils.isIntelliJ();
  }
}
