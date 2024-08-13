// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.application.options.OptionsApplicabilityFilter;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.bookmark.BookmarksListener;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.ClassEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.GeneralModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.application.options.OptionId.PROJECT_VIEW_SHOW_VISIBILITY_ICONS;
import static com.intellij.ui.treeStructure.Tree.AUTO_SCROLL_FROM_SOURCE_BLOCKED;

@State(name = "ProjectView", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE), getStateRequiresEdt = true)
public class ProjectViewImpl extends ProjectView implements PersistentStateComponent<Element>, QuickActionProvider, BusyObject {
  private static final Logger LOG = Logger.getInstance(ProjectViewImpl.class);
  private static final Key<String> ID_KEY = Key.create("pane-id");
  private static final Key<String> SUB_ID_KEY = Key.create("pane-sub-id");
  public static final @NonNls String ANDROID_VIEW_ID = "AndroidView";

  private final CopyPasteDelegator copyPasteDelegator;
  private boolean isInitialized;
  private final AtomicBoolean isExtensionsLoaded = new AtomicBoolean(false);
  private final @NotNull Project project;

  private boolean firstShow = true;

  private final ProjectViewState currentState;
  // + options
  private final Option abbreviatePackageNames = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return flattenPackages.isSelected() && flattenPackages.isEnabled(pane) && pane.supportsAbbreviatePackageNames();
    }

    @Override
    public boolean isAlwaysVisible() {
      var pane = getCurrentProjectViewPane();
      return pane != null && pane.supportsAbbreviatePackageNames() && flattenPackages.isEnabled(pane);
    }

    @Override
    public boolean isSelected() {
      return currentState.getAbbreviatePackageNames();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) {
        return;
      }

      boolean updated = selected != isSelected();
      currentState.setAbbreviatePackageNames(selected);
      getDefaultState().setAbbreviatePackageNames(selected);
      getGlobalOptions().setAbbreviatePackages(selected);
      if (updated) {
        updatePanes(false);
      }
    }
  };

  private final Option myAutoscrollFromSource = new Option() {
    @Override
    public boolean isSelected() {
      return currentState.getAutoscrollFromSource();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) {
        return;
      }

      currentState.setAutoscrollFromSource(selected);
      getDefaultState().setAutoscrollFromSource(selected);
      getGlobalOptions().setAutoscrollFromSource(selected);
      if (selected && !autoScrollFromSourceHandler.isCurrentProjectViewPaneFocused()) {
        SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because Always Select Opened File has been turned on");
        autoScrollFromSourceHandler.scrollFromSource();
      }
    }
  };

  private final Option myAutoscrollToSource = new Option() {
    @Override
    public boolean isSelected() {
      return currentState.getAutoscrollToSource();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) {
        return;
      }

      currentState.setAutoscrollToSource(selected);
      getDefaultState().setAutoscrollToSource(selected);
      getGlobalOptions().setAutoscrollToSource(selected);
    }
  };

  private final Option myOpenDirectoriesWithSingleClick = new Option() {
    @Override
    public boolean isSelected() {
      return currentState.getOpenDirectoriesWithSingleClick();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      currentState.setOpenDirectoriesWithSingleClick(selected);
      getDefaultState().setOpenDirectoriesWithSingleClick(selected);
      getGlobalOptions().setOpenDirectoriesWithSingleClick(selected);
      var pane = getCurrentProjectViewPane();
      if (pane != null) {
        var tree = pane.getTree();
        if (tree != null) {
          tree.setToggleClickCount(selected ? 1 : 2);
        }
      }
    }
  };

  private final Option openInPreviewTab = new Option() {
    @Override
    public boolean isSelected() {
      return UISettings.getInstance().getOpenInPreviewTabIfPossible();
    }

    @Override
    public void setSelected(boolean selected) {
      UISettings.getInstance().setOpenInPreviewTabIfPossible(selected);
    }
  };

  private final Option compactDirectories = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsCompactDirectories();
    }

    @Override
    public boolean isSelected() {
      return currentState.getCompactDirectories();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) {
        return;
      }

      boolean updated = selected != isSelected();
      currentState.setCompactDirectories(selected);
      getDefaultState().setCompactDirectories(selected);
      getGlobalOptions().setCompactDirectories(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option flattenModules = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsFlattenModules();
    }

    @Override
    public boolean isSelected() {
      return currentState.getFlattenModules();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) {
        return;
      }

      boolean updated = selected != isSelected();
      currentState.setFlattenModules(selected);
      getDefaultState().setFlattenModules(selected);
      getGlobalOptions().setFlattenModules(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option flattenPackages = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return ProjectViewDirectoryHelper.getInstance(project).supportsFlattenPackages();
    }

    @Override
    public boolean isSelected() {
      return currentState.getFlattenPackages();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setFlattenPackages(selected);
      getDefaultState().setFlattenPackages(selected);
      getGlobalOptions().setFlattenPackages(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option myFoldersAlwaysOnTop = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsFoldersAlwaysOnTop();
    }

    @Override
    public boolean isSelected() {
      return currentState.getFoldersAlwaysOnTop();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setFoldersAlwaysOnTop(selected);
      getDefaultState().setFoldersAlwaysOnTop(selected);
      getGlobalOptions().setFoldersAlwaysOnTop(selected);
      if (updated) updatePanes(true);
    }
  };

  private final Option myShowScratchesAndConsoles = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsShowScratchesAndConsoles();
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowScratchesAndConsoles();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowScratchesAndConsoles(selected);
      getDefaultState().setShowScratchesAndConsoles(selected);
      getGlobalOptions().setShowScratchesAndConsoles(selected);
      if (updated) updatePanes(true);
    }
  };

  private final Option myHideEmptyMiddlePackages = new Option() {
    @Override
    public @NotNull String getName() {
      return flattenPackages.isSelected()
             ? IdeBundle.message("action.hide.empty.middle.packages")
             : IdeBundle.message("action.compact.empty.middle.packages");
    }

    @Override
    public String getDescription() {
      return flattenPackages.isSelected()
             ? IdeBundle.message("action.show.hide.empty.middle.packages")
             : IdeBundle.message("action.show.compact.empty.middle.packages");
    }

    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsHideEmptyMiddlePackages() && ProjectViewDirectoryHelper.getInstance(project).supportsHideEmptyMiddlePackages();
    }

    @Override
    public boolean isSelected() {
      return currentState.getHideEmptyMiddlePackages();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setHideEmptyMiddlePackages(selected);
      getDefaultState().setHideEmptyMiddlePackages(selected);
      getGlobalOptions().setHideEmptyPackages(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option manualOrder = new Option() {
    @Override
    public @NotNull String getName() {
      AbstractProjectViewPane pane = getCurrentProjectViewPane();
      return pane != null
             ? pane.getManualOrderOptionText()
             : IdeBundle.message("action.manual.order");
    }

    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsManualOrder();
    }

    @Override
    public boolean isSelected() {
      return currentState.getManualOrder();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setManualOrder(selected);
      getDefaultState().setManualOrder(selected);
      getGlobalOptions().setManualOrder(selected);
      if (updated) updatePanes(true);
    }
  };

  private final Option myShowExcludedFiles = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsShowExcludedFiles();
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowExcludedFiles();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowExcludedFiles(selected);
      getDefaultState().setShowExcludedFiles(selected);
      getGlobalOptions().setShowExcludedFiles(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option myShowLibraryContents = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsShowLibraryContents();
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowLibraryContents();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowLibraryContents(selected);
      getDefaultState().setShowLibraryContents(selected);
      getGlobalOptions().setShowLibraryContents(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option myShowMembers = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return isShowMembersOptionSupported();
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowMembers();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowMembers(selected);
      getDefaultState().setShowMembers(selected);
      getGlobalOptions().setShowMembers(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option myShowModules = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return pane.supportsShowModules();
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowModules();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowModules(selected);
      getDefaultState().setShowModules(selected);
      getGlobalOptions().setShowModules(selected);
      if (updated) updatePanes(false);
    }
  };

  private final Option myShowVisibilityIcons = new Option() {
    @Override
    public boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return OptionsApplicabilityFilter.isApplicable(PROJECT_VIEW_SHOW_VISIBILITY_ICONS);
    }

    @Override
    public boolean isSelected() {
      return currentState.getShowVisibilityIcons();
    }

    @Override
    public void setSelected(boolean selected) {
      if (project.isDisposed()) return;
      boolean updated = selected != isSelected();
      currentState.setShowVisibilityIcons(selected);
      getDefaultState().setShowVisibilityIcons(selected);
      getGlobalOptions().setShowVisibilityIcons(selected);
      if (updated) updatePanes(false);
    }
  };

  private String currentViewId;
  private String currentViewSubId;
  // - options

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyAutoScrollFromSourceHandler autoScrollFromSourceHandler;
  private final AtomicBoolean myAutoScrollOnFocusEditor = new AtomicBoolean(true);

  private final IdeView myIdeView = new IdeViewForProjectViewPane(this::getCurrentProjectViewPane);

  private SimpleToolWindowPanel panel;
  private final Map<String, AbstractProjectViewPane> idToPane = new ConcurrentHashMap<>();
  private final Collection<AbstractProjectViewPane> uninitializedPanes = ConcurrentHashMap.newKeySet();

  private DefaultActionGroup actionGroup;
  private @Nullable String savedPaneId;
  private @Nullable String savedPaneSubId;
  private static final @NonNls String ELEMENT_NAVIGATOR = "navigator";
  private static final @NonNls String ELEMENT_PANES = "panes";
  private static final @NonNls String ELEMENT_PANE = "pane";
  private static final @NonNls String ATTRIBUTE_CURRENT_VIEW = "currentView";
  private static final @NonNls String ATTRIBUTE_CURRENT_SUBVIEW = "currentSubView";

  private static final String ATTRIBUTE_ID = "id";
  private JPanel viewContentPanel;
  private static final Comparator<AbstractProjectViewPane> PANE_WEIGHT_COMPARATOR = Comparator.comparingInt(AbstractProjectViewPane::getWeight);
  private final JComponent myDataProvider;
  private final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
  private final Map<String, Element> myUninitializedPaneState = new HashMap<>();
  private final Map<String, MySelectInTarget> mySelectInTargets = new ConcurrentHashMap<>();

  private record MySelectInTarget(SelectInTarget target, int weight) {}
  private static final Comparator<MySelectInTarget> TARGET_WEIGHT_COMPARATOR = Comparator.comparingInt(MySelectInTarget::weight);
  private ContentManager contentManager;

  public ProjectViewImpl(@NotNull Project project) {
    this.project = project;
    currentState = ProjectViewState.getInstance(project);

    constructUi();

    autoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();

    myDataProvider = UiDataProvider.wrapComponent(panel, sink -> uiDataSnapshot(sink));
    ClientProperty.put(myDataProvider, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, buildNotInHierarchyIterable());

    copyPasteDelegator = new CopyPasteDelegator(this.project, panel);
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myAutoscrollToSource.isSelected() || openInPreviewTab.isSelected();
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myAutoscrollToSource.setSelected(state);
      }

      @Override
      public boolean isAutoScrollEnabledFor(@NotNull VirtualFile file) {
        if (!super.isAutoScrollEnabledFor(file)) return false;
        AbstractProjectViewPane pane = getCurrentProjectViewPane();
        return pane == null || pane.isAutoScrollEnabledFor(file);
      }

      @Override
      protected String getActionName() {
        return ActionsBundle.message("action.ProjectView.AutoscrollToSource.text" );
      }

      @Override
      protected String getActionDescription() {
        return ActionsBundle.message("action.ProjectView.AutoscrollToSource.description" );
      }

      @Override
      protected boolean needToCheckFocus() {
        AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
        return currentProjectViewPane == null || !currentProjectViewPane.isAutoScrollEnabledWithoutFocus();
      }
    };

    AbstractProjectViewPane.EP.addExtensionPointListener(project, new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull AbstractProjectViewPane extension, @NotNull PluginDescriptor pluginDescriptor) {
        reloadPanes();
      }

      @Override
      public void extensionRemoved(@NotNull AbstractProjectViewPane extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (idToPane.containsKey(extension.getId()) || uninitializedPanes.contains(extension)) {
          reloadPanes();
        }
        else {
          Disposer.dispose(extension);
        }
      }
    }, project);
  }

  static final class MyToolWindowManagerListener implements ToolWindowManagerListener {

    @Override
    public void toolWindowShown(@NotNull ToolWindow toolWindow) {
      if (!ToolWindowId.PROJECT_VIEW.equals(toolWindow.getId())) {
        return;
      }

      Project project = toolWindow.getProject();
      ProjectView projectView = ProjectView.getInstance(project);
      AbstractProjectViewPane pane = projectView.getCurrentProjectViewPane();
      if (pane == null) {
        return;
      }

      Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length != 1 || !GeneralModuleType.TYPE_ID.equals(modules[0].getModuleTypeName())) {
        JTree tree = pane.getTree();
        if (tree != null && projectView instanceof ProjectViewImpl impl && impl.firstShow) {
          impl.firstShow = false;
          TreeUtil.promiseSelectFirst(tree).onSuccess(tree::expandPath);
        }
      }

      if (projectView.isAutoscrollFromSource(pane.getId())) {
        SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because the project view is shown");

        if (projectView instanceof ProjectViewImpl impl) {
          impl.autoScrollFromSourceHandler.scrollFromSource();
        }
      }
    }
  }

  private void constructUi() {
    viewContentPanel = new JPanel();
    viewContentPanel.putClientProperty(FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB, true);
    panel = new SimpleToolWindowPanel(true).setProvideQuickActions(false);
    panel.setContent(viewContentPanel);
  }

  @Override
  public @NotNull String getName() {
    return IdeUICustomization.getInstance().getProjectViewTitle(project);
  }

  @Override
  public @NotNull List<AnAction> getActions(boolean originalProvider) {
    DefaultActionGroup views = DefaultActionGroup.createPopupGroup(() -> LangBundle.message("action.change.view.text"));

    ChangeViewAction lastHeader = null;
    for (int i = 0; i < contentManager.getContentCount(); i++) {
      Content each = contentManager.getContent(i);
      if (each == null) continue;

      String id = each.getUserData(ID_KEY);
      String subId = each.getUserData(SUB_ID_KEY);
      ChangeViewAction newHeader = new ChangeViewAction(id, subId);

      if (lastHeader != null) {
        boolean lastHasKids = lastHeader.mySubId != null;
        boolean newHasKids = newHeader.mySubId != null;
        if (lastHasKids != newHasKids ||
            lastHasKids && !Strings.areSameInstance(lastHeader.myId, newHeader.myId)) {
          views.add(Separator.getInstance());
        }
      }

      views.add(newHeader);
      lastHeader = newHeader;
    }
    List<AnAction> result = new ArrayList<>();
    result.add(views);
    result.add(Separator.getInstance());

    if (actionGroup != null) {
      List<AnAction> secondary = new ArrayList<>();
      for (AnAction each : actionGroup.getChildren(ActionManager.getInstance())) {
        if (actionGroup.isPrimary(each)) {
          result.add(each);
        }
        else {
          secondary.add(each);
        }
      }

      result.add(Separator.getInstance());
      result.addAll(secondary);
    }

    return result;
  }

  private static ProjectViewState getDefaultState() {
    return ProjectViewState.getDefaultInstance();
  }

  private final class ChangeViewAction extends AnAction {
    private final @NotNull String myId;
    private final @Nullable String mySubId;

    private ChangeViewAction(@NotNull String id, @Nullable String subId) {
      myId = id;
      mySubId = subId;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      AbstractProjectViewPane pane = getProjectViewPaneById(myId);
      e.getPresentation().setText(mySubId != null ? pane.getPresentableSubIdName(mySubId) : pane.getTitle());
      e.getPresentation().setIcon(mySubId != null ? pane.getPresentableSubIdIcon(mySubId) : pane.getIcon());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      changeView(myId, mySubId);
    }
  }

  @Override
  @CalledInAny
  public synchronized void addProjectPane(final @NotNull AbstractProjectViewPane pane) {
    uninitializedPanes.add(pane);
    SelectInTarget selectInTarget = pane.createSelectInTarget();
    String id = selectInTarget.getMinorViewId();
    if (pane.getId().equals(id)) {
      mySelectInTargets.put(id, new MySelectInTarget(selectInTarget, pane.getWeight()));
    }
    else {
      try {
        LOG.error("Unexpected SelectInTarget: " + selectInTarget.getClass() + "\n  created by project pane:" + pane.getClass());
      }
      catch (AssertionError ignored) {
      }
    }
    if (isInitialized) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        doAddUninitializedPanes();
      } else {
        ApplicationManager.getApplication().invokeLater(this::doAddUninitializedPanes);
      }
    }
  }

  @Override
  public synchronized void removeProjectPane(@NotNull AbstractProjectViewPane pane) {
    ThreadingAssertions.assertEventDispatchThread();
    uninitializedPanes.remove(pane);
    //assume we are completely initialized here
    @NotNull String idToRemove = pane.getId();

    if (!idToPane.containsKey(idToRemove)) return;
    for (int i = getContentManager().getContentCount() - 1; i >= 0; i--) {
      Content content = getContentManager().getContent(i);
      String id = content != null ? content.getUserData(ID_KEY) : null;
      if (idToRemove.equals(id)) {
        getContentManager().removeContent(content, true);
      }
    }
    idToPane.remove(idToRemove);
    mySelectInTargets.remove(idToRemove);
    viewSelectionChanged();
  }

  private synchronized void doAddUninitializedPanes() {
    for (AbstractProjectViewPane pane : uninitializedPanes) {
      doAddPane(pane);
    }

    Content[] contents = getContentManager().getContents();
    for (int i = 1; i < contents.length; i++) {
      Content content = contents[i];
      Content prev = contents[i - 1];
      if (!StringUtil.equals(content.getUserData(ID_KEY), prev.getUserData(ID_KEY)) &&
          prev.getUserData(SUB_ID_KEY) != null && content.getSeparator() == null) {
        content.setSeparator("");
      }
    }

    String selectID = null;
    String selectSubID = null;

    String savedPaneId = this.savedPaneId;
    if (savedPaneId == null) {
      savedPaneId = getDefaultViewId();
      savedPaneSubId = null;
    }

    // try to find saved selected view...
    for (Content content : contents) {
      String id = content.getUserData(ID_KEY);
      String subId = content.getUserData(SUB_ID_KEY);
      if (id != null &&
          id.equals(savedPaneId) &&
          StringUtil.equals(subId, savedPaneSubId)) {
        selectID = id;
        selectSubID = subId;
        this.savedPaneId = null;
        savedPaneSubId = null;
        break;
      }
    }

    // saved view not found (plugin disabled, ID changed etc.) - select first available view...
    if (selectID == null && contents.length > 0 && currentViewId == null) {
      Content content = contents[0];
      selectID = content.getUserData(ID_KEY);
      selectSubID = content.getUserData(SUB_ID_KEY);
    }

    if (selectID != null) {
      changeView(selectID, selectSubID);
    }

    uninitializedPanes.clear();
  }

  private void doAddPane(final @NotNull AbstractProjectViewPane newPane) {
    ThreadingAssertions.assertEventDispatchThread();
    int index;
    final ContentManager manager = getContentManager();
    for (index = 0; index < manager.getContentCount(); index++) {
      Content content = manager.getContent(index);
      if (content == null) {
        continue;
      }
      String id = content.getUserData(ID_KEY);
      if (id == null) {
        continue;
      }
      AbstractProjectViewPane pane = idToPane.get(id);

      int comp = PANE_WEIGHT_COMPARATOR.compare(pane, newPane);
      LOG.assertTrue(comp != 0, "Project view pane " + newPane + " has the same weight as " + pane +
                                ". Please make sure that you overload getWeight() and return a distinct weight value.");
      if (comp > 0) {
        break;
      }
    }

    final @NotNull String id = newPane.getId();
    idToPane.put(id, newPane);
    String[] subIds = newPane.getSubIds();
    subIds = subIds.length == 0 ? new String[]{null} : subIds;
    boolean first = true;
    for (String subId : subIds) {
      final String title = subId != null ? newPane.getPresentableSubIdName(subId) : newPane.getTitle();
      final Content content = getContentManager().getFactory().createContent(getComponent(), title, false);
      content.setTabName(title);
      content.putUserData(ID_KEY, id);
      content.putUserData(SUB_ID_KEY, subId);
      content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
      Icon icon = subId != null ? newPane.getPresentableSubIdIcon(subId) : newPane.getIcon();
      if (!ExperimentalUI.isNewUI()) {
        content.setIcon(icon);
        content.setPopupIcon(icon);
      }
      content.setPreferredFocusedComponent(() -> {
        final AbstractProjectViewPane current = getCurrentProjectViewPane();
        return current != null ? current.getComponentToFocus() : null;
      });
      content.setBusyObject(this);
      if (first && subId != null) {
        content.setSeparator(newPane.getTitle());
      }
      manager.addContent(content, index++);
      first = false;
    }
  }

  private void showPane(@NotNull AbstractProjectViewPane newPane) {
    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    Object selectedUserObject;
    if (currentPane != null) {
      if (currentPane != newPane) {
        currentPane.saveExpandedPaths();
      }
      selectedUserObject = ArrayUtil.getFirstElement(currentPane.getSelectedUserObjects());
    }
    else {
      selectedUserObject = null;
    }
    viewContentPanel.removeAll();
    JComponent component = newPane.createComponent();
    UIUtil.removeScrollBorder(component);
    viewContentPanel.setLayout(new BorderLayout());
    viewContentPanel.add(component, BorderLayout.CENTER);
    currentViewId = newPane.getId();
    String newSubId = currentViewSubId = newPane.getSubId();
    viewContentPanel.revalidate();
    viewContentPanel.repaint();
    createToolbarActions(newPane);

    if (newPane.myTree != null) {
      myAutoScrollToSourceHandler.install(newPane.myTree);
      myAutoScrollToSourceHandler.onMouseClicked(newPane.myTree);
      newPane.myTree.setToggleClickCount(myOpenDirectoriesWithSingleClick.isSelected() ? 1 : 2);
    }

    newPane.restoreExpandedPaths();
    if (selectedUserObject != null && newSubId != null) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Re-selecting " + selectedUserObject + " after switching to " + currentViewId);
      }
      project.getService(SelectInProjectViewImpl.class).ensureSelected(
        currentViewId,
        null,
        () -> ContainerUtil.getFirstItem(newPane.getElementsFromNode(selectedUserObject)),
        true,
        false,
        null
      );
    }
    project.getMessageBus().syncPublisher(ProjectViewListener.TOPIC).paneShown(newPane, currentPane);

    logProjectViewPaneChangedEvent(project, currentPane, newPane);
  }

  private static void logProjectViewPaneChangedEvent(@NotNull Project project,
                                                     @Nullable AbstractProjectViewPane currentPane,
                                                     @NotNull AbstractProjectViewPane newPane) {
    List<EventPair<?>> events = new ArrayList<>(4);

    events.add(ProjectViewPaneChangesCollector.TO_PROJECT_VIEW.with(newPane.getClass()));
    NamedScope selectedScope = newPane instanceof ScopeViewPane ? ((ScopeViewPane)newPane).getSelectedScope() : null;
    if (selectedScope != null) {
      events.add(ProjectViewPaneChangesCollector.TO_SCOPE.with(selectedScope.getClass()));
    }
    if (currentPane != null) {
      events.add(ProjectViewPaneChangesCollector.FROM_PROJECT_VIEW.with(currentPane.getClass()));
      selectedScope = currentPane instanceof ScopeViewPane ? ((ScopeViewPane)currentPane).getSelectedScope() : null;
      if (selectedScope != null) {
        events.add(ProjectViewPaneChangesCollector.FROM_SCOPE.with(selectedScope.getClass()));
      }
    }

    ProjectViewPaneChangesCollector.CHANGED.log(project, events);
  }

  // public for tests
  public synchronized void setupImpl(@NotNull ToolWindow toolWindow) {
    setupImpl(toolWindow, true);
  }

  // public for tests
  public synchronized void setupImpl(@NotNull ToolWindow toolWindow, final boolean loadPaneExtensions) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isInitialized) return;

    var loadStatisticsReporter = new ProjectViewInitReporter();
    project.getMessageBus().connect(loadStatisticsReporter).subscribe(ProjectViewListener.TOPIC, loadStatisticsReporter);
    project.getService(ProjectViewInitNotifier.class).initStarted();

    actionGroup = new DefaultActionGroup();

    autoScrollFromSourceHandler.install();

    contentManager = toolWindow.getContentManager();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO);
      toolWindow.setAdditionalGearActions(actionGroup);
      toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    GuiUtils.replaceJSplitPaneWithIDEASplitter(panel);
    SwingUtilities.invokeLater(() -> splitterProportions.restoreSplitterProportions(panel));

    if (loadPaneExtensions) {
      ensurePanesLoaded();
    }
    isInitialized = true;
    doAddUninitializedPanes();

    getContentManager().addContentManagerListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          viewSelectionChanged();
        }
      }
    });
    viewSelectionChanged();
    setupToolwindowActions(toolWindow);

    Object multicaster = EditorFactory.getInstance().getEventMulticaster();
    if (multicaster instanceof EditorEventMulticasterEx ex) {
      ex.addFocusChangeListener(new FocusChangeListener() {
        @Override
        public void focusLost(@NotNull Editor editor, @NotNull FocusEvent event) {
          autoScrollFromSourceHandler.cancelAllRequests();
          myAutoScrollOnFocusEditor.set(true);
        }

        @Override
        public void focusGained(@NotNull Editor editor, @NotNull FocusEvent event) {
          if (!Registry.is("ide.autoscroll.from.source.on.focus.gained")) return;
          if (!myAutoScrollOnFocusEditor.getAndSet(true)) return;
          if (isAutoscrollFromSource(getCurrentViewId())) {
            autoScrollFromSourceHandler.addRequest(() -> {
              // ensure that it is still enabled after a while
              if (isAutoscrollFromSource(getCurrentViewId())) {
                if (findFileEditor(editor) != null) {
                  SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because the editor has gained focus");
                  autoScrollFromSourceHandler.scrollFromSource();
                }
              }
            });
          }
        }
      }, project);
    }
  }

  private void setupToolwindowActions(@NotNull ToolWindow toolWindow) {
    List<AnAction> titleActions = new ArrayList<>();
    createTitleActions(titleActions);
    if (!titleActions.isEmpty()) {
      toolWindow.setTitleActions(titleActions);
    }

    List<AnAction> tabActions = new ArrayList<>();
    createTabActions(tabActions);
    if (!tabActions.isEmpty()) {
      ((ToolWindowEx)toolWindow).setTabActions(tabActions.toArray(AnAction[]::new));
    }
  }

  @ApiStatus.Internal
  public synchronized void reloadPanes() {
    if (project.isDisposed() || !isExtensionsLoaded.get()) return; // panes will be loaded later

    Map<String, AbstractProjectViewPane> newPanes = loadPanes();
    Map<AbstractProjectViewPane, Boolean> oldPanes = new IdentityHashMap<>();
    uninitializedPanes.forEach(pane -> oldPanes.put(pane, pane == newPanes.get(pane.getId())));
    idToPane.forEach((id, pane) -> oldPanes.put(pane, pane == newPanes.get(id)));
    oldPanes.forEach((pane, exists) -> {
      if (Boolean.FALSE.equals(exists)) {
        removeProjectPane(pane);
        Disposer.dispose(pane);
      }
    });
    for (AbstractProjectViewPane pane : newPanes.values()) {
      if (!Boolean.TRUE.equals(oldPanes.get(pane)) && pane.isInitiallyVisible()) {
        addProjectPane(pane);
      }
    }
  }

  private void ensurePanesLoaded() {
    if (project.isDisposed() || isExtensionsLoaded.getAndSet(true)) {
      // avoid recursive loading
      return;
    }

    for (AbstractProjectViewPane pane : loadPanes().values()) {
      if (pane.isInitiallyVisible()) {
        addProjectPane(pane);
      }
    }
  }

  private @NotNull Map<String, AbstractProjectViewPane> loadPanes() {
    Map<String, AbstractProjectViewPane> map = new LinkedHashMap<>();
    List<AbstractProjectViewPane> toSort = new ArrayList<>(AbstractProjectViewPane.EP.getExtensions(project));
    toSort.sort(PANE_WEIGHT_COMPARATOR);
    for (AbstractProjectViewPane pane : toSort) {
      AbstractProjectViewPane added = map.computeIfAbsent(pane.getId(), id -> pane);
      if (pane != added) {
        LOG.warn("ignore duplicated pane with id=" + pane.getId() + "\nold " + added.getClass() + "\nnew " + pane.getClass());
      }
      else {
        Element element = myUninitializedPaneState.remove(pane.getId());
        if (element != null) {
          applyPaneState(pane, element);
        }
      }
    }
    return map;
  }

  private void viewSelectionChanged() {
    Content content = getContentManager().getSelectedContent();
    if (content == null) {
      return;
    }

    String id = content.getUserData(ID_KEY);
    String subId = content.getUserData(SUB_ID_KEY);
    if (Objects.equals(id, currentViewId) && Objects.equals(subId, currentViewSubId)) {
      return;
    }

    AbstractProjectViewPane newPane = getProjectViewPaneById(id);
    if (newPane == null) {
      return;
    }

    newPane.setSubId(subId);
    showPane(newPane);
    ProjectViewSelectInTarget target = getProjectViewSelectInTarget(newPane);
    if (target != null) {
      target.setSubId(subId);
    }
    if (isAutoscrollFromSource(id)) {
      SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because the project view has changed panes");
      autoScrollFromSourceHandler.scrollFromSource();
    }
  }

  private void createToolbarActions(@NotNull AbstractProjectViewPane pane) {
    if (actionGroup == null) return;
    actionGroup.removeAll();

    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("ProjectView.ToolWindow.SecondaryActions");
    for (AnAction action : group.getChildActionsOrStubs()) {
      actionGroup.addAction(action).setAsSecondary(true);
    }

    pane.addToolbarActions(actionGroup);
  }

  protected void createTitleActions(@NotNull List<? super AnAction> titleActions) {
    AnAction action = ActionManager.getInstance().getAction("ProjectViewToolbar");
    if (action != null) titleActions.add(action);
  }

  protected void createTabActions(@NotNull List<? super AnAction> tabActions) {
    AnAction action = ActionManager.getInstance().getAction("ProjectViewTabToolbar");
    if (action != null) tabActions.add(action);
  }

  protected boolean isShowMembersOptionSupported() {
    return true;
  }

  @Override
  @CalledInAny
  public AbstractProjectViewPane getProjectViewPaneById(String id) {
    if (id == null) {
      return null;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // most tests don't need all panes to be loaded
      ensurePanesLoaded();
    }

    final AbstractProjectViewPane pane = idToPane.get(id);
    if (pane != null) {
      return pane;
    }
    for (AbstractProjectViewPane viewPane : uninitializedPanes) {
      if (viewPane.getId().equals(id)) {
        return viewPane;
      }
    }
    return null;
  }

  @Override
  public AbstractProjectViewPane getCurrentProjectViewPane() {
    if (project.isDisposed()) return null;
    ProjectViewCurrentPaneProvider currentPaneProvider = ProjectViewCurrentPaneProvider.getInstance(project);
    final String currentProjectViewPaneId = currentPaneProvider != null
                                            ? currentPaneProvider.getCurrentPaneId()
                                            : currentViewId;
    return currentProjectViewPaneId != null
           ? getProjectViewPaneById(currentProjectViewPaneId)
           : null;
  }

  @Override
  public void refresh() {
    AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
    if (currentProjectViewPane != null) {
      // may be null for e.g. default project
      IconDeferrer.getInstance().clearCache(); // icons may have changed
      currentProjectViewPane.updateFromRoot(false);
    }
  }

  @Override
  public void select(final Object element, VirtualFile file, boolean requestFocus) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("select: element=" + element + ", file=" + file + ", requestFocus=" + requestFocus);
    }
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null) {
      myAutoScrollOnFocusEditor.set(!requestFocus);
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Delegating to AbstractProjectViewPane, auto scroll enabled="
                                                 + myAutoScrollOnFocusEditor.get());
      }
      viewPane.select(element, file, requestFocus);
    }
  }

  void select(
    @NotNull Supplier<Object> elementSupplier,
    @NotNull VirtualFile virtualFile,
    boolean requestFocus,
    @Nullable ActionCallback result
  ) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "ProjectViewImpl.select: " +
        "elementSupplier=" + elementSupplier +
        ", file=" + virtualFile +
        ", requestFocus=" + requestFocus +
        ", result=" + result
      );
    }
    SelectInProjectViewImplKt.getLOG().debug("Starting a read action in background to retrieve the element from the supplier");
    ReadAction
      .nonBlocking(elementSupplier::get)
      .finishOnUiThread(
        ModalityState.defaultModalityState(),
        element -> {
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Retrieved the element from the supplier: " + element);
          }
          var callback = selectCB(element, virtualFile, requestFocus);
          if (result != null) {
            callback.notify(result);
          }
        }
      ).coalesceBy("ProjectViewImpl.select", this)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public @NotNull ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("ProjectViewImpl.selectCB: element=" + element + ", file=" + file + ", requestFocus=" + requestFocus);
    }
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane instanceof AbstractProjectViewPaneWithAsyncSupport) {
      myAutoScrollOnFocusEditor.set(!requestFocus);
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Delegating to AbstractProjectViewPaneWithAsyncSupport, auto scroll enabled="
                                                 + myAutoScrollOnFocusEditor.get());
      }
      return ((AbstractProjectViewPaneWithAsyncSupport)viewPane).selectCB(element, file, requestFocus);
    }
    select(element, file, requestFocus);
    return ActionCallback.DONE;
  }

  @Override
  public JComponent getComponent() {
    return myDataProvider;
  }

  @Override
  public String getCurrentViewId() {
    return currentViewId;
  }

  @Nullable SelectInTarget getCurrentSelectInTarget() {
    return getSelectInTarget(getCurrentViewId());
  }

  private @Nullable SelectInTarget getSelectInTarget(String id) {
    if (id == null) {
      return null;
    }
    MySelectInTarget target = mySelectInTargets.get(id);
    if (target == null) {
      return null;
    }
    return target.target();
  }

  ProjectViewSelectInTarget getProjectViewSelectInTarget(@NotNull AbstractProjectViewPane pane) {
    SelectInTarget target = getSelectInTarget(pane.getId());
    return target instanceof ProjectViewSelectInTarget
           ? (ProjectViewSelectInTarget)target
           : null;
  }

  @Override
  public PsiElement getParentOfCurrentSelection() {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane == null) {
      return null;
    }

    TreePath path = viewPane.getSelectedPath();
    if (path == null) {
      return null;
    }
    path = path.getParentPath();
    if (path == null) {
      return null;
    }

    ProjectViewNode<?> descriptor = TreeUtil.getLastUserObject(ProjectViewNode.class, path);
    if (descriptor != null) {
      Object element = descriptor.getValue();
      if (element instanceof PsiElement psiElement) {
        if (!psiElement.isValid()) return null;
        return psiElement;
      }
      else {
        return null;
      }
    }
    return null;
  }

  public ContentManager getContentManager() {
    if (contentManager == null) {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW).getContentManager();
    }
    return contentManager;
  }

  @Override
  public void changeView(@NotNull String viewId) {
    changeView(viewId, null);
  }

  @Override
  public void changeView(@NotNull String viewId, @Nullable String subId) {
    changeViewCB(viewId, subId);
  }

  @Override
  public @NotNull ActionCallback changeViewCB(@NotNull String viewId, @Nullable String subId) {
    AbstractProjectViewPane pane = getProjectViewPaneById(viewId);
    LOG.assertTrue(pane != null, "Project view pane not found: " + viewId + "; subId:" + subId + "; project: " + project);

    boolean hasSubViews = pane.getSubIds().length > 0;
    if (hasSubViews) {
      if (subId == null) {
        // we try not to change subview
        // get currently selected subId from the pane
        subId = pane.getSubId();
      }
    }
    else if (subId != null) {
      LOG.error("View doesn't have subviews: " + viewId + "; subId:" + subId + "; project: " + project);
    }

    if (viewId.equals(currentViewId) && Objects.equals(subId, currentViewSubId)) {
      return ActionCallback.REJECTED;
    }

    // at this point null subId means that view has no subviews OR subview was never selected
    // we then search first content with the right viewId ignoring subIds of contents
    for (Content content : getContentManager().getContents()) {
      if (viewId.equals(content.getUserData(ID_KEY)) && (subId == null || subId.equals(content.getUserData(SUB_ID_KEY)))) {
        return getContentManager().setSelectedContentCB(content);
      }
    }

    return ActionCallback.REJECTED;
  }

  private @NotNull Iterable<? extends Component> buildNotInHierarchyIterable() {
    return () -> JBIterable.from(new ArrayList<>(idToPane.values()))
      .map(pane -> {
        JComponent last = null;
        for (Component c : UIUtil.uiParents(pane.getComponentToFocus(), false)) {
          if (c == myDataProvider || !(c instanceof JComponent)) return null;
          last = (JComponent)c;
        }
        return last;
      })
      .filter(Component.class)
      .iterator();
  }

  private void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.CUT_PROVIDER, copyPasteDelegator.getCutProvider());
    sink.set(PlatformDataKeys.COPY_PROVIDER, copyPasteDelegator.getCopyProvider());
    sink.set(PlatformDataKeys.PASTE_PROVIDER, copyPasteDelegator.getPasteProvider());
    sink.set(LangDataKeys.IDE_VIEW, myIdeView);
    sink.set(PlatformCoreDataKeys.HELP_ID, HelpID.PROJECT_VIEWS);
    sink.set(QuickActionProvider.KEY, ProjectViewImpl.this);
    AbstractProjectViewPane selectedPane = getCurrentProjectViewPane();
    if (selectedPane != null) {
      DataSink.uiDataSnapshot(sink, selectedPane);
    }
  }

  @Override
  public void selectPsiElement(@NotNull PsiElement element, boolean requestFocus) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    select(element, virtualFile, requestFocus);
  }

  @Override
  public void loadState(@NotNull Element parentNode) {
    Element navigatorElement = parentNode.getChild(ELEMENT_NAVIGATOR);
    if (navigatorElement != null) {
      savedPaneId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_VIEW);
      if (savedPaneId == null) {
        savedPaneSubId = null;
      }
      else {
        savedPaneSubId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_SUBVIEW);
      }

      try {
        splitterProportions.readExternal(navigatorElement);
      }
      catch (InvalidDataException ignored) {
      }
    }

    Element panesElement = parentNode.getChild(ELEMENT_PANES);
    if (panesElement != null) {
      readPaneState(panesElement);
    }
  }

  @Override
  public @NotNull String getDefaultViewId() {
    //noinspection SpellCheckingInspection
    if ("AndroidStudio".equals(PlatformUtils.getPlatformPrefix()) && !Boolean.getBoolean("studio.projectview")) {
      // the default in Android Studio unless studio.projectview is set: issuetracker.google.com/37091465
      return ANDROID_VIEW_ID;
    }
    else {
      for (AbstractProjectViewPane extension : AbstractProjectViewPane.EP.getExtensions(project)) {
        if (extension.isDefaultPane(project)) {
          return extension.getId();
        }
      }
      return ProjectViewPane.ID;
    }
  }

  private void readPaneState(@NotNull Element panesElement) {
    List<Element> paneElements = panesElement.getChildren(ELEMENT_PANE);
    for (Element paneElement : paneElements) {
      String paneId = paneElement.getAttributeValue(ATTRIBUTE_ID);
      if (StringUtil.isEmptyOrSpaces(paneId)) {
        continue;
      }

      AbstractProjectViewPane pane = idToPane.get(paneId);
      if (pane != null) {
        applyPaneState(pane, paneElement);
      }
      else {
        myUninitializedPaneState.put(paneId, paneElement);
      }
    }
  }

  private static void applyPaneState(@NotNull AbstractProjectViewPane pane, @NotNull Element element) {
    try {
      pane.readExternal(element);
    }
    catch (InvalidDataException ignored) {
    }
  }

  @Override
  public Element getState() {
    Element parentNode = new Element("projectView");
    Element navigatorElement = new Element(ELEMENT_NAVIGATOR);

    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    if (currentPane != null) {
      String subId = currentPane.getSubId();
      navigatorElement.setAttribute(ATTRIBUTE_CURRENT_VIEW, currentPane.getId());
      if (subId != null) {
        navigatorElement.setAttribute(ATTRIBUTE_CURRENT_SUBVIEW, subId);
      }
    }

    splitterProportions.saveSplitterProportions(panel);
    try {
      splitterProportions.writeExternal(navigatorElement);
    }
    catch (WriteExternalException ignored) {
    }

    if (!JDOMUtil.isEmpty(navigatorElement)) {
      parentNode.addContent(navigatorElement);
    }

    Element panesElement = new Element(ELEMENT_PANES);
    writePaneState(panesElement);
    parentNode.addContent(panesElement);
    return parentNode;
  }

  private void writePaneState(@NotNull Element panesElement) {
    for (AbstractProjectViewPane pane : idToPane.values()) {
      Element paneElement = new Element(ELEMENT_PANE);
      paneElement.setAttribute(ATTRIBUTE_ID, pane.getId());
      try {
        pane.writeExternal(paneElement);
      }
      catch (WriteExternalException e) {
        continue;
      }
      panesElement.addContent(paneElement);
    }
    for (Element element : myUninitializedPaneState.values()) {
      panesElement.addContent(element.clone());
    }
  }

  private static ProjectViewSharedSettings getGlobalOptions() {
    return ProjectViewSharedSettings.Companion.getInstance();
  }

  @Override
  public boolean isAutoscrollToSource(String paneId) {
    return myAutoscrollToSource.isSelected() && myAutoscrollToSource.isEnabled(paneId);
  }

  public void setAutoscrollToSource(boolean autoscrollMode, String paneId) {
    if (myAutoscrollToSource.isEnabled(paneId)) {
      myAutoscrollToSource.setSelected(autoscrollMode);
    }
  }

  @Override
  public boolean isAutoscrollFromSource(String paneId) {
    return isAutoscrollFromSourceEnabled(paneId) && isAutoscrollFromSourceNotBlocked(paneId);
  }

  private boolean isAutoscrollFromSourceEnabled(String paneId) {
    if (project.isDisposed()) return false;
    if (!myAutoscrollFromSource.isSelected()) return false;
    if (!myAutoscrollFromSource.isEnabled(paneId)) return false;
    return true;
  }

  private boolean isAutoscrollFromSourceNotBlocked(String paneId) {
    AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
    if (pane == null || pane != getCurrentProjectViewPane()) return false;
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (toolWindow == null || !toolWindow.isVisible()) return false;
    JTree tree = pane.getTree();
    if (tree == null) return false;
    return !ClientProperty.isTrue(tree, AUTO_SCROLL_FROM_SOURCE_BLOCKED);
  }

  public void setAutoscrollFromSource(boolean autoscrollMode, String paneId) {
    if (myAutoscrollFromSource.isEnabled(paneId)) {
      myAutoscrollFromSource.setSelected(autoscrollMode);
    }
  }

  @Override
  public boolean isFlattenPackages(String paneId) {
    return flattenPackages.isSelected() && flattenPackages.isEnabled(paneId);
  }

  public void setFlattenPackages(String paneId, boolean flattenPackages) {
    if (this.flattenPackages.isEnabled(paneId)) {
      this.flattenPackages.setSelected(flattenPackages);
    }
  }

  @Override
  public boolean isFoldersAlwaysOnTop(String paneId) {
    return myFoldersAlwaysOnTop.isSelected() && myFoldersAlwaysOnTop.isEnabled(paneId);
  }

  /**
   * @deprecated use {@link ProjectView#isFoldersAlwaysOnTop(String)} instead
   */
  @Deprecated(forRemoval = true)
  public boolean isFoldersAlwaysOnTop() {
    return myFoldersAlwaysOnTop.isSelected() && myFoldersAlwaysOnTop.isEnabled();
  }

  public void setFoldersAlwaysOnTop(boolean foldersAlwaysOnTop) {
    if (myFoldersAlwaysOnTop.isEnabled()) {
      myFoldersAlwaysOnTop.setSelected(foldersAlwaysOnTop);
    }
  }

  @Override
  public boolean isShowMembers(String paneId) {
    return myShowMembers.isSelected() && myShowMembers.isEnabled(paneId);
  }

  @Override
  public boolean isHideEmptyMiddlePackages(String paneId) {
    return myHideEmptyMiddlePackages.isSelected() && myHideEmptyMiddlePackages.isEnabled(paneId);
  }

  @Override
  public boolean isAbbreviatePackageNames(String paneId) {
    return abbreviatePackageNames.isSelected() && abbreviatePackageNames.isEnabled(paneId);
  }

  @Override
  public boolean isShowExcludedFiles(String paneId) {
    return myShowExcludedFiles.isSelected() && myShowExcludedFiles.isEnabled(paneId);
  }

  @Override
  public boolean isShowVisibilityIcons(String paneId) {
    return myShowVisibilityIcons.isSelected() && myShowVisibilityIcons.isEnabled(paneId);
  }

  @Override
  public boolean isShowLibraryContents(String paneId) {
    return myShowLibraryContents.isSelected() && myShowLibraryContents.isEnabled(paneId);
  }

  @Override
  public void setShowLibraryContents(@NotNull String paneId, boolean showLibraryContents) {
    if (myShowLibraryContents.isEnabled(paneId)) myShowLibraryContents.setSelected(showLibraryContents);
  }

  @Override
  public boolean isShowModules(String paneId) {
    return myShowModules.isSelected() && myShowModules.isEnabled(paneId);
  }

  @Override
  public void setShowModules(@NotNull String paneId, boolean showModules) {
    if (myShowModules.isEnabled(paneId)) myShowModules.setSelected(showModules);
  }

  @Override
  public boolean isFlattenModules(String paneId) {
    return flattenModules.isSelected() && flattenModules.isEnabled(paneId);
  }

  @Override
  public void setFlattenModules(@NotNull String paneId, boolean flattenModules) {
    if (this.flattenModules.isEnabled(paneId)) this.flattenModules.setSelected(flattenModules);
  }

  @Override
  public boolean isShowURL(String paneId) {
    return Registry.is("project.tree.structure.show.url");
  }

  @Override
  public boolean isShowScratchesAndConsoles(String paneId) {
    return myShowScratchesAndConsoles.isEnabled(paneId) ? myShowScratchesAndConsoles.isSelected() : false;
  }

  @Override
  public void setHideEmptyPackages(@NotNull String paneId, boolean hideEmptyPackages) {
    if (myHideEmptyMiddlePackages.isEnabled(paneId)) myHideEmptyMiddlePackages.setSelected(hideEmptyPackages);
  }

  @Override
  public boolean isUseFileNestingRules(String paneId) {
    if (!currentState.getUseFileNestingRules()) return false;
    if (paneId == null) {
      return false;
    }
    AbstractProjectViewPane pane = idToPane.get(paneId);
    return pane != null && pane.isFileNestingEnabled();
  }

  @Override
  public void setUseFileNestingRules(boolean useFileNestingRules) {
    if (project.isDisposed()) return;
    boolean updated = useFileNestingRules != currentState.getUseFileNestingRules();
    currentState.setUseFileNestingRules(useFileNestingRules);
    getDefaultState().setUseFileNestingRules(useFileNestingRules);
    if (updated) updatePanes(false);
  }

  @Override
  public boolean isCompactDirectories(String paneId) {
    return compactDirectories.isSelected() && compactDirectories.isEnabled(paneId);
  }

  @Override
  public void setCompactDirectories(@NotNull String paneId, boolean compactDirectories) {
    if (this.compactDirectories.isEnabled(paneId)) this.compactDirectories.setSelected(compactDirectories);
  }

  @Override
  public void setAbbreviatePackageNames(@NotNull String paneId, boolean abbreviatePackageNames) {
    if (this.abbreviatePackageNames.isEnabled(paneId)) {
      this.abbreviatePackageNames.setSelected(abbreviatePackageNames);
    }
  }

  private static final class SelectionInfo {
    private final @NotNull Object @NotNull [] elements;

    private SelectionInfo(@NotNull Object @NotNull [] elements) {
      this.elements = elements;
    }

    public void apply(AbstractProjectViewPane viewPane) {
      if (viewPane == null) {
        return;
      }

      JTree tree = viewPane.myTree;
      List<TreeVisitor> visitors = AbstractProjectViewPane.createVisitors(elements);
      if (1 == visitors.size()) {
        TreeUtil.promiseSelect(tree, visitors.get(0));
      }
      else if (!visitors.isEmpty()) {
        TreeUtil.promiseSelect(tree, visitors.stream());
      }
    }

    public static @NotNull SelectionInfo create(final AbstractProjectViewPane viewPane) {
      if (viewPane == null) {
        return new SelectionInfo(ArrayUtilRt.EMPTY_OBJECT_ARRAY);
      }

      List<Object> selectedElements = Collections.emptyList();
      TreePath[] selectionPaths = viewPane.getSelectionPaths();
      if (selectionPaths != null) {
        selectedElements = new ArrayList<>();
        for (TreePath path : selectionPaths) {
          NodeDescriptor<?> descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, path);
          if (descriptor != null) {
            Object element = descriptor.getElement();
            if (element != null) {
              selectedElements.add(element);
            }
          }
        }
      }
      return new SelectionInfo(selectedElements.toArray());
    }
  }

  private final class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private MyAutoScrollFromSourceHandler() {
      super(ProjectViewImpl.this.project, viewContentPanel, ProjectViewImpl.this.project);
    }

    void cancelAllRequests() {
      myAlarm.cancelAllRequests();
    }

    void addRequest(@NotNull Runnable request) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(request, getAlarmDelay(), getModalityState());
    }

    @Override
    protected void selectElementFromEditor(@NotNull FileEditor fileEditor) {
      if (myProject.isDisposed() || !viewContentPanel.isShowing()) return;
      if (isAutoscrollFromSource(getCurrentViewId()) && !isCurrentProjectViewPaneFocused()) {
        SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because the selected editor tab has been changed");
        scrollFromSource(fileEditor, false);
      }
    }

    void scrollFromSource() {
      scrollFromSource(null, false);
    }

    private void scrollFromSource(@Nullable FileEditor fileEditor, boolean invokedManually) {
      myProject.getService(SelectInProjectViewImpl.class).selectInCurrentTarget(fileEditor, invokedManually);
    }

    private boolean isCurrentProjectViewPaneFocused() {
      AbstractProjectViewPane pane = getCurrentProjectViewPane();
      return pane != null && IJSwingUtilities.hasFocus(pane.getComponentToFocus());
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return myAutoscrollFromSource.isSelected();
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      myAutoscrollFromSource.setSelected(state);
    }

    @Override
    protected String getActionName() {
      return ActionsBundle.message("action.ProjectView.AutoscrollFromSource.text");
    }

    @Override
    protected String getActionDescription() {
      return ActionsBundle.message("action.ProjectView.AutoscrollFromSource.description");
    }
  }

  @Override
  public boolean isManualOrder(String paneId) {
    return manualOrder.isSelected() && manualOrder.isEnabled(paneId);
  }

  @Override
  public void setManualOrder(@NotNull String paneId, final boolean enabled) {
    if (manualOrder.isEnabled(paneId)) {
      manualOrder.setSelected(enabled);
    }
  }

  @Override
  public boolean isSortByType(String paneId) {
    return getSortKey(paneId) == NodeSortKey.BY_TYPE;
  }

  @Override
  public void setSortByType(@NotNull String paneId, final boolean sortByType) {
    setSortKey(paneId, sortByType ? NodeSortKey.BY_TYPE : ProjectViewSettings.Immutable.DEFAULT.getSortKey());
  }

  @Override
  public @NotNull NodeSortKey getSortKey(String paneId) {
    var currentSortKey = currentState.getSortKey();
    var pane = getProjectViewPaneById(paneId);
    return pane != null && pane.supportsSortKey(currentSortKey) ? currentSortKey : ProjectViewSettings.Immutable.DEFAULT.getSortKey();
  }

  @Override
  public void setSortKey(@NotNull String paneId, @NotNull NodeSortKey sortKey) {
    setSortKey(this, getProjectViewPaneById(paneId), sortKey);
  }

  private static void setSortKey(@Nullable ProjectViewImpl view, @Nullable AbstractProjectViewPane pane, @NotNull NodeSortKey sortKey) {
    getDefaultState().setSortKey(sortKey);
    getGlobalOptions().setSortKey(sortKey);
    if (view == null || (pane != null && !pane.supportsSortKey(sortKey))) {
      return;
    }
    boolean updated = view.currentState.getSortKey() != sortKey;
    view.currentState.setSortKey(sortKey);
    if (updated) {
      view.updatePanes(true);
    }
  }

  boolean isSelectOpenedFileEnabled() {
    return !isAutoscrollFromSourceEnabled(currentViewId) || AdvancedSettings.getBoolean("project.view.do.not.autoscroll.to.libraries");
  }

  void selectOpenedFileUsingLastFocusedEditor() {
    // invokeLater is needed here to give FileEditorManagerImpl time to figure out which editor is the last focused one.
    // If the IDE frame has just became active because the Select Opened File button was clicked,
    // then the editor may temporarily get focus before the Project View is focused.
    // This needs to be undone before we can select the right file.
    // And no, there's no way to prevent that temporary focus.
    // See com.intellij.openapi.fileEditor.impl.EditorsSplitters.MyFocusWatcher for the gore details.
    SwingUtilities.invokeLater(() -> {
      selectOpenedFile(getLastFocusedEditor());
    });
  }

  void selectOpenedFile() {
    selectOpenedFile(null);
  }

  private void selectOpenedFile(@Nullable FileEditor editor) {
    SelectInProjectViewImplKt.getLOG().debug("Invoking scroll from source because Select Opened File was performed manually");
    autoScrollFromSourceHandler.scrollFromSource(editor, true);
  }

  private @Nullable FileEditor getLastFocusedEditor() {
    FileEditor result = null;
    var fileEditorManager = FileEditorManager.getInstance(project);
    if (fileEditorManager instanceof FileEditorManagerImpl fileEditorManagerImpl) {
      result = fileEditorManagerImpl.getLastFocusedEditor();
    }
    if (result != null) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug(
          "Forcing use of the last focused editor to select opened file: " + result
        );
      }
    }
    return result;
  }

  private @Nullable FileEditor findFileEditor(Editor editor) {
    JComponent component = editor.getComponent();
    for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
      if (SwingUtilities.isDescendingFrom(component, fileEditor.getComponent())) {
        return fileEditor;
      }
    }
    return null;
  }

  @Override
  public @NotNull Collection<String> getPaneIds() {
    return Collections.unmodifiableCollection(idToPane.keySet());
  }

  @Override
  @CalledInAny
  public @NotNull Collection<SelectInTarget> getSelectInTargets() {
    ensurePanesLoaded();
    return mySelectInTargets.values().stream().sorted(TARGET_WEIGHT_COMPARATOR).map(MySelectInTarget::target).toList();
  }

  @Override
  public @NotNull ActionCallback getReady(@NotNull Object requestor) {
    AbstractProjectViewPane pane = currentViewSubId == null ? null : idToPane.get(currentViewSubId);
    if (pane == null) {
      pane = currentViewId == null ? null : idToPane.get(currentViewId);
    }
    return pane != null ? pane.getReady(requestor) : ActionCallback.DONE;
  }

  private void updatePanes(boolean withComparator) {
    for (AbstractProjectViewPane pane : idToPane.values()) {
      JTree tree = pane.getTree();
      if (tree != null) {
        SelectionInfo info = pane.getId().equals(currentViewId) ? SelectionInfo.create(pane) : null;
        if (withComparator) {
          pane.installComparator();
        }
        pane.updateFromRoot(false);
        if (info != null) {
          info.apply(pane);
        }
      }
    }
    if (withComparator) {
      // sort nodes in the BookmarksView
      project.getMessageBus().syncPublisher(BookmarksListener.TOPIC).structureChanged(null);
    }
  }


  abstract class Option implements ToggleOptionAction.Option {
    @Override
    public boolean isEnabled() {
      return isEnabled(getCurrentViewId());
    }

    boolean isEnabled(@Nullable String paneId) {
      AbstractProjectViewPane pane = paneId == null ? null : idToPane.get(paneId);
      return pane != null ? isEnabled(pane) : ApplicationManager.getApplication().isUnitTestMode();
    }

    boolean isEnabled(@NotNull AbstractProjectViewPane pane) {
      return true;
    }
  }

  static class Action extends ToggleOptionAction implements DumbAware {
    private Action(@NotNull Function<? super ProjectViewImpl, ? extends Option> optionSupplier) {
      super(event -> {
        Project project = event.getProject();
        ProjectView view = project == null || project.isDisposed() ? null : getInstance(project);
        return view instanceof ProjectViewImpl ? optionSupplier.apply((ProjectViewImpl)view) : null;
      });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    static final class AbbreviatePackageNames extends Action {
      AbbreviatePackageNames() {
        super(view -> view.abbreviatePackageNames);
      }
    }

    static final class AutoscrollFromSource extends Action implements ActionRemoteBehaviorSpecification.Frontend {
      AutoscrollFromSource() {
        super(view -> view.myAutoscrollFromSource);
      }
    }

    static final class AutoscrollToSource extends Action implements ActionRemoteBehaviorSpecification.Frontend {
      AutoscrollToSource() {
        super(view -> view.myAutoscrollToSource);
      }
    }

    static final class OpenDirectoriesWithSingleClick extends Action implements ActionRemoteBehaviorSpecification.Frontend {
      OpenDirectoriesWithSingleClick() {
        super(view -> view.myOpenDirectoriesWithSingleClick);
      }
    }

    static final class OpenInPreviewTab extends Action implements ActionRemoteBehaviorSpecification.Frontend {
      OpenInPreviewTab() {
        super(view -> view.openInPreviewTab);
      }
    }

    static final class CompactDirectories extends Action {
      CompactDirectories() {
        super(view -> view.compactDirectories);
      }
    }

    static final class FlattenModules extends Action {
      FlattenModules() {
        super(view -> view.flattenModules);
      }
    }

    static final class FlattenPackages extends Action {
      FlattenPackages() {
        super(view -> view.flattenPackages);
      }
    }

    static final class FoldersAlwaysOnTop extends Action {
      FoldersAlwaysOnTop() {
        super(view -> view.myFoldersAlwaysOnTop);
      }
    }

    static final class ShowScratchesAndConsoles extends Action {
      ShowScratchesAndConsoles() {
        super(view -> view.myShowScratchesAndConsoles);
      }
    }

    static final class HideEmptyMiddlePackages extends Action {
      HideEmptyMiddlePackages() {
        super(view -> view.myHideEmptyMiddlePackages);
      }
    }

    static final class ManualOrder extends Action {
      ManualOrder() {
        super(view -> view.manualOrder);
      }
    }

    static final class ShowExcludedFiles extends Action {
      ShowExcludedFiles() {
        super(view -> view.myShowExcludedFiles);
      }
    }

    static final class ShowLibraryContents extends Action {
      ShowLibraryContents() {
        super(view -> view.myShowLibraryContents);
      }
    }

    static final class ShowMembers extends Action {
      ShowMembers() {
        super(view -> view.myShowMembers);
      }
    }

    static final class ShowModules extends Action {
      ShowModules() {
        super(view -> view.myShowModules);
      }
    }

    static final class ShowVisibilityIcons extends Action {
      ShowVisibilityIcons() {
        super(view -> view.myShowVisibilityIcons);
      }
    }

    abstract static class SortKeyAction extends DumbAwareToggleAction {
      private final @NotNull NodeSortKey mySortKey;

      SortKeyAction(@NotNull NodeSortKey sortKey) {
        mySortKey = sortKey;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        var view = getProjectView(e);
        return view != null && view.currentState.getSortKey() == mySortKey;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean selected) {
        var view = getProjectView(e);
        setSortKey(view, null, mySortKey);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        var presentation = e.getPresentation();
        presentation.setEnabledAndVisible(ApplicationManager.getApplication().isUnitTestMode());
        if (ActionPlaces.isPopupPlace(e.getPlace())) {
          presentation.setIcon(null);
        }
        var pane = getCurrentProjectViewPane(e);
        if (pane == null) {
          return;
        }
        presentation.setEnabledAndVisible(pane.supportsSortKey(mySortKey));
      }

      protected static @Nullable AbstractProjectViewPane getCurrentProjectViewPane(@NotNull AnActionEvent e) {
        var view = getProjectView(e);
        if (view == null) {
          return null;
        }
        return view.getCurrentProjectViewPane();
      }

      private static @Nullable ProjectViewImpl getProjectView(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        ProjectView view = project == null || project.isDisposed() ? null : getInstance(project);
        return view instanceof ProjectViewImpl ? (ProjectViewImpl) view : null;
      }
    }

    static final class SortByName extends SortKeyAction {
      SortByName() {
        super(NodeSortKey.BY_NAME);
      }
    }

    static final class SortByType extends SortKeyAction {
      SortByType() {
        super(NodeSortKey.BY_TYPE);
      }
    }

    static final class SortByTimeDescending extends SortKeyAction {
      SortByTimeDescending() {
        super(NodeSortKey.BY_TIME_DESCENDING);
      }
    }

    static final class SortByTimeAscending extends SortKeyAction {
      SortByTimeAscending() {
        super(NodeSortKey.BY_TIME_ASCENDING);
      }
    }
  }

  private static final class ProjectViewPaneChangesCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("project.view.pane.changes", 2);
    private static final ClassEventField TO_PROJECT_VIEW = EventFields.Class("to_class_name");
    private static final ClassEventField TO_SCOPE = EventFields.Class("to_scope_class_name");
    private static final ClassEventField FROM_PROJECT_VIEW = EventFields.Class("from_class_name");
    private static final ClassEventField FROM_SCOPE = EventFields.Class("from_scope_class_name");
    private static final VarargEventId CHANGED =
      GROUP.registerVarargEvent("changed", TO_PROJECT_VIEW, TO_SCOPE, FROM_PROJECT_VIEW, FROM_SCOPE);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }

  private static final class ProjectViewInitReporter implements Disposable, ProjectViewListener {

    private long initStarted;

    @Override
    public void initStarted() {
      initStarted = System.currentTimeMillis();
    }

    @Override
    public void initCachedNodesLoaded() {
      if (initStarted == 0L) {
        LOG.warn(new Throwable("Cached nodes are loaded, but init hasn't even started yet"));
        return;
      }
      ProjectViewPerformanceCollector.logCachedStateLoadDuration(System.currentTimeMillis() - initStarted);
    }

    @Override
    public void initCompleted() {
      if (initStarted == 0L) {
        LOG.warn(new Throwable("Project view initialized, but init hasn't even started yet"));
        return;
      }
      ProjectViewPerformanceCollector.logFullStateLoadDuration(System.currentTimeMillis() - initStarted);
      Disposer.dispose(this);
    }

    @Override
    public void dispose() { }
  }
}