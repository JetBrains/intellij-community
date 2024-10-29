// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.DataSink;
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
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
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
import org.jetbrains.annotations.*;

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
  public static final @NonNls String ID = "Scope";
  private static final Logger LOG = Logger.getInstance(ScopeViewPane.class);
  private final IdeView myIdeView = new IdeViewForProjectViewPane(() -> this);
  private final NamedScopesHolder myDependencyValidationManager;
  private final NamedScopesHolder myNamedScopeManager;
  private final @NotNull AtomicReference<ScopeViewTreeModel> myTreeModel = new AtomicReference<>();
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

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public int getWeight() {
    return 4;
  }

  @Override
  public @NotNull String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  @Override
  public boolean isFileNestingEnabled() {
    return true;
  }

  @Override
  public @NotNull JComponent createComponent() {
    ScopeViewTreeModel myTreeModel;
    if (this.myTreeModel.get() == null) {
      myTreeModel = new ScopeViewTreeModel(myProject, new ProjectViewSettings.Delegate(myProject, ID));
      myTreeModel.setStructureProvider(CompoundTreeStructureProvider.get(myProject));
      myTreeModel.setNodeDecorator(CompoundProjectViewNodeDecorator.get(myProject));
      myTreeModel.setFilter(getFilter(getSubId()));
      myTreeModel.setComparator(createComparator());
      this.myTreeModel.set(myTreeModel);
    }
    else {
      myTreeModel = this.myTreeModel.get();
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
    var model = myTreeModel.get();
    if (model == null) {
      // not initialized yet
      return ActionCallback.REJECTED;
    }

    saveExpandedPaths();
    model.invalidate(null);
    restoreExpandedPaths(); // TODO:check
    return ActionCallback.DONE;
  }

  @Override
  public @NotNull SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  @Override
  public void select(Object object, VirtualFile file, boolean requestFocus) {
    if (myTreeModel.get() == null) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Can NOT select " + object + " / " + file + " in " + this + " because the scope pane isn't initialized yet");
      }
      // not initialized yet
      return;
    }
    if (file == null) {
      LOG.warn(new IllegalArgumentException("ScopeViewPane.select: file==null, object=" + object));
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Can NOT select " + object + " / " + file + " in " + this + " because the file is null");
      }
      return; // Filters don't accept null files anyway, so just do nothing.
    }

    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    SmartPsiElementPointer<PsiElement> pointer = null;
    if (element != null) {
      if (element.isValid()) {
        pointer = SmartPointerManager.createPointer(element);
      }
      else {
        LOG.warn("ScopeViewPane.select(object=" + object + ",file=" + file + ",requestFocus=" + requestFocus + "): element invalidated");
      }
    }
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("Select " + object + " / " + file + " in " + this);
    }
    myProject.getService(SelectInProjectViewImpl.class).selectInScopeViewPane(this, pointer, file, requestFocus);
  }

  private void selectScopeView(String subId) {
    ProjectView view = myProject.isDisposed() ? null : ProjectView.getInstance(myProject);
    if (view != null) {
      view.changeView(getId(), subId);
    }
  }

  @ApiStatus.Internal
  public void select(@Nullable SmartPsiElementPointer<PsiElement> pointer, VirtualFile file, boolean requestFocus, VirtualFileFilter filter) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "ScopeViewPane.select: " +
        "pane=" + this +
        ", pointer=" + pointer +
        ", file=" + file +
        ", requestFocus=" + requestFocus +
        ", filter=" + filter
      );
    }
    String subId = filter.toString();
    if (!Objects.equals(subId, getSubId())) {
      if (requestFocus) {
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug(
            "Selected subId=" + getSubId() +
            ", requested subId=" + subId +
            ", changing the scope"
          );
        }
        selectScopeView(subId);
      }
      else {
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug(
            "Selected subId=" + getSubId() +
            ", requested subId=" + subId +
            ", changing not allowed because requestFocus=false, aborting"
          );
        }
        return;
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("select element: ", (pointer == null ? null : pointer.getElement()), " in file: ", file);
    }
    TreeVisitor visitor = AbstractProjectViewPane.createVisitorByPointer(pointer, file);
    if (visitor == null) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Not selecting anything because both the pointer and file are null");
      }
      return;
    }
    JTree tree = myTree;
    SelectInProjectViewImplKt.getLOG().debug("Start updating the tree. Will continue once updated");
    myTreeModel.get().getUpdater().updateImmediately(() -> {
      SelectInProjectViewImplKt.getLOG().debug("Updated. Start expanding the tree and looking for the path to select");
      TreeState.expand(tree, promise -> TreeUtil.visit(tree, visitor, path -> {
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("Expanded. The path to select is " + path);
        }
        if (selectPath(tree, path) || pointer == null || Registry.is("async.project.view.support.extra.select.disabled")) {
          promise.setResult(null);
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Selected. Done");
          }
        }
        else {
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Not selected. Trying to look for the file without the pointer instead");
          }
          // try to search the specified file instead of element,
          // because Kotlin files cannot represent containing functions
          TreeUtil.visit(tree, AbstractProjectViewPane.createVisitor(file), path2 -> {
            selectPath(tree, path2);
            promise.setResult(null);
            if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
              SelectInProjectViewImplKt.getLOG().debug("Found and selected " + path2);
            }
          });
        }
      }));
    });
  }

  private boolean selectPath(@NotNull JTree tree, TreePath path) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("selectPath: " + path + " in " + this);
    }
    if (path == null) {
      return false;
    }
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPath(tree, path); // select and scroll to center
    return true;
  }

  @Override
  public @NotNull ActionCallback getReady(@NotNull Object requestor) {
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

  @Override
  public @NotNull String getPresentableSubIdName(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter == null ? getTitle() : filter.getScope().getPresentableName();
  }

  @Override
  public @NotNull Icon getPresentableSubIdIcon(@NotNull String subId) {
    NamedScopeFilter filter = getFilter(subId);
    return filter != null ? filter.getScope().getIcon() : getIcon();
  }

  @Override
  public @Nullable Object getValueFromNode(@Nullable Object node) {
    var model = myTreeModel.get();
    if (model == null) {
      // not initialized yet
      return null;
    }
    return model.getContent(node);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(LangDataKeys.IDE_VIEW, myIdeView);
  }

  public void updateSelectedScope() {
    // not initialized yet
    var model = myTreeModel.get();
    if (model == null) {
      return;
    }
    model.setFilter(getFilter(getSubId()));
  }

  public @Nullable NamedScope getSelectedScope() {
    NamedScopeFilter filter = getFilter(getSubId());
    return filter == null ? null : filter.getScope();
  }

  @CalledInAny
  @ApiStatus.Internal
  public @NotNull Iterable<NamedScopeFilter> getFilters() {
    Map<String, NamedScopeFilter> map = myFilters.get();
    return map == null ? Collections.emptyList() : map.values();
  }

  @CalledInAny
  @ApiStatus.Internal
  public @Nullable NamedScopeFilter getCurrentFilter() {
    var model = myTreeModel.get();
    return model == null ? null : model.getFilter();
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

  @Override
  public String toString() {
    return "ScopeViewPane{id=" + getId() + ",subId=" + getSubId() + "}";
  }
}
