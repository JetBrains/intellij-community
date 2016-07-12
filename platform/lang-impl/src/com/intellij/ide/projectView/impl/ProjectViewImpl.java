/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.ProjectTopics;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.HelpID;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

@State(name = "ProjectView", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ProjectViewImpl extends ProjectView implements PersistentStateComponent<Element>, Disposable, QuickActionProvider, BusyObject  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.ProjectViewImpl");
  private static final Key<String> ID_KEY = Key.create("pane-id");
  private static final Key<String> SUB_ID_KEY = Key.create("pane-sub-id");
  private final CopyPasteDelegator myCopyPasteDelegator;
  private boolean isInitialized;
  private boolean myExtensionsLoaded = false;
  @NotNull private final Project myProject;

  // + options
  private final Map<String, Boolean> myFlattenPackages = new THashMap<String, Boolean>();
  private static final boolean ourFlattenPackagesDefaults = false;
  private final Map<String, Boolean> myShowMembers = new THashMap<String, Boolean>();
  private static final boolean ourShowMembersDefaults = false;
  private final Map<String, Boolean> myManualOrder = new THashMap<String, Boolean>();
  private static final boolean ourManualOrderDefaults = false;
  private final Map<String, Boolean> mySortByType = new THashMap<String, Boolean>();
  private static final boolean ourSortByTypeDefaults = false;
  private final Map<String, Boolean> myShowModules = new THashMap<String, Boolean>();
  private static final boolean ourShowModulesDefaults = true;
  private final Map<String, Boolean> myShowLibraryContents = new THashMap<String, Boolean>();
  private static final boolean ourShowLibraryContentsDefaults = true;
  private final Map<String, Boolean> myHideEmptyPackages = new THashMap<String, Boolean>();
  private static final boolean ourHideEmptyPackagesDefaults = true;
  private final Map<String, Boolean> myAbbreviatePackageNames = new THashMap<String, Boolean>();
  private static final boolean ourAbbreviatePackagesDefaults = false;
  private final Map<String, Boolean> myAutoscrollToSource = new THashMap<String, Boolean>();
  private final Map<String, Boolean> myAutoscrollFromSource = new THashMap<String, Boolean>();
  private static final boolean ourAutoscrollFromSourceDefaults = false;
  
  private boolean myFoldersAlwaysOnTop = true;
  

  private String myCurrentViewId;
  private String myCurrentViewSubId;
  // - options

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyAutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  private final IdeView myIdeView = new MyIdeView();
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private SimpleToolWindowPanel myPanel;
  private final Map<String, AbstractProjectViewPane> myId2Pane = new LinkedHashMap<String, AbstractProjectViewPane>();
  private final Collection<AbstractProjectViewPane> myUninitializedPanes = new THashSet<AbstractProjectViewPane>();

  static final DataKey<ProjectViewImpl> DATA_KEY = DataKey.create("com.intellij.ide.projectView.impl.ProjectViewImpl");

  private DefaultActionGroup myActionGroup;
  private String mySavedPaneId = ProjectViewPane.ID;
  private String mySavedPaneSubId;
  //private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  //private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");
  @NonNls private static final String ELEMENT_NAVIGATOR = "navigator";
  @NonNls private static final String ELEMENT_PANES = "panes";
  @NonNls private static final String ELEMENT_PANE = "pane";
  @NonNls private static final String ATTRIBUTE_CURRENT_VIEW = "currentView";
  @NonNls private static final String ATTRIBUTE_CURRENT_SUBVIEW = "currentSubView";
  @NonNls private static final String ELEMENT_FLATTEN_PACKAGES = "flattenPackages";
  @NonNls private static final String ELEMENT_SHOW_MEMBERS = "showMembers";
  @NonNls private static final String ELEMENT_SHOW_MODULES = "showModules";
  @NonNls private static final String ELEMENT_SHOW_LIBRARY_CONTENTS = "showLibraryContents";
  @NonNls private static final String ELEMENT_HIDE_EMPTY_PACKAGES = "hideEmptyPackages";
  @NonNls private static final String ELEMENT_ABBREVIATE_PACKAGE_NAMES = "abbreviatePackageNames";
  @NonNls private static final String ELEMENT_AUTOSCROLL_TO_SOURCE = "autoscrollToSource";
  @NonNls private static final String ELEMENT_AUTOSCROLL_FROM_SOURCE = "autoscrollFromSource";
  @NonNls private static final String ELEMENT_SORT_BY_TYPE = "sortByType";
  @NonNls private static final String ELEMENT_FOLDERS_ALWAYS_ON_TOP = "foldersAlwaysOnTop";
  @NonNls private static final String ELEMENT_MANUAL_ORDER = "manualOrder";

  private static final String ATTRIBUTE_ID = "id";
  private JPanel myViewContentPanel;
  private static final Comparator<AbstractProjectViewPane> PANE_WEIGHT_COMPARATOR = (o1, o2) -> o1.getWeight() - o2.getWeight();
  private final FileEditorManager myFileEditorManager;
  private final MyPanel myDataProvider;
  private final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
  private final MessageBusConnection myConnection;
  private final Map<String, Element> myUninitializedPaneState = new HashMap<String, Element>();
  private final Map<String, SelectInTarget> mySelectInTargets = new LinkedHashMap<String, SelectInTarget>();
  private ContentManager myContentManager;

  public ProjectViewImpl(@NotNull Project project, final FileEditorManager fileEditorManager, final ToolWindowManagerEx toolWindowManager) {
    myProject = project;

    constructUi();

    myFileEditorManager = fileEditorManager;

    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        refresh();
      }
    });

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();

    myDataProvider = new MyPanel();
    myDataProvider.add(myPanel, BorderLayout.CENTER);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, myPanel) {
      @Override
      @NotNull
      protected PsiElement[] getSelectedElements() {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        return viewPane == null ? PsiElement.EMPTY_ARRAY : viewPane.getSelectedPSIElements();
      }
    };
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return isAutoscrollToSource(myCurrentViewId);
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        setAutoscrollToSource(state, myCurrentViewId);
      }
    };
    toolWindowManager.addToolWindowManagerListener(new ToolWindowManagerAdapter(){
      private boolean toolWindowVisible;

      @Override
      public void stateChanged() {
        ToolWindow window = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (window == null) return;
        if (window.isVisible() && !toolWindowVisible) {
          String id = getCurrentViewId();
          if (isAutoscrollToSource(id)) {
            AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();

            if (currentProjectViewPane != null) {
              myAutoScrollToSourceHandler.onMouseClicked(currentProjectViewPane.getTree());
            }
          }
          if (isAutoscrollFromSource(id)) {
            myAutoScrollFromSourceHandler.setAutoScrollEnabled(true);
          }
        }
        toolWindowVisible = window.isVisible();
      }
    });
  }

  private void constructUi() {
    myViewContentPanel = new JPanel();
    myPanel = new SimpleToolWindowPanel(true).setProvideQuickActions(false);
    myPanel.setContent(myViewContentPanel);
  }

  @Override
  public String getName() {
    return "Project";
  }

  @Override
  @NotNull
  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();

    DefaultActionGroup views = new DefaultActionGroup("Change View", true);
    boolean lastHeaderHadKids = false;
    for (int i = 0; i < myContentManager.getContentCount(); i++) {
      Content each = myContentManager.getContent(i);
      if (each != null) {
        if (each.getUserData(SUB_ID_KEY) == null) {
          if (lastHeaderHadKids) {
            views.add(new Separator());
          } else {
            if (i + 1 < myContentManager.getContentCount()) {
              Content next = myContentManager.getContent(i + 1);
              if (next != null) {
                if (next.getUserData(SUB_ID_KEY) != null) {
                  views.add(new Separator());
                }
              }
            }
          }
        }
        else {
          lastHeaderHadKids = true;
        }

        views.add(new ChangeViewAction(each.getUserData(ID_KEY), each.getUserData(SUB_ID_KEY)));
      }
    }
    result.add(views);
    result.add(new Separator());


    List<AnAction> secondary = new ArrayList<AnAction>();
    if (myActionGroup != null) {
      AnAction[] kids = myActionGroup.getChildren(null);
      for (AnAction each : kids) {
        if (myActionGroup.isPrimary(each)) {
          result.add(each);
        }
        else {
          secondary.add(each);
        }
      }
    }
    result.add(new Separator());
    result.addAll(secondary);

    return result;
  }

  private class ChangeViewAction extends AnAction {
    private final String myId;
    private final String mySubId;

    private ChangeViewAction(@NotNull String id, String subId) {
      myId = id;
      mySubId = subId;
    }

    @Override
    public void update(AnActionEvent e) {
      AbstractProjectViewPane pane = getProjectViewPaneById(myId);
      e.getPresentation().setText(pane.getTitle() + (mySubId != null ? (" - " + pane.getPresentableSubIdName(mySubId)) : ""));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      changeView(myId, mySubId);
    }
  }

  @Override
  public boolean isCycleRoot() {
    return false;
  }

  @Override
  public synchronized void addProjectPane(@NotNull final AbstractProjectViewPane pane) {
    myUninitializedPanes.add(pane);
    SelectInTarget selectInTarget = pane.createSelectInTarget();
    if (selectInTarget != null) {
      mySelectInTargets.put(pane.getId(), selectInTarget);
    }
    if (isInitialized) {
      doAddUninitializedPanes();
    }
  }

  @Override
  public synchronized void removeProjectPane(@NotNull AbstractProjectViewPane pane) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myUninitializedPanes.remove(pane);
    //assume we are completely initialized here
    String idToRemove = pane.getId();

    if (!myId2Pane.containsKey(idToRemove)) return;
    pane.removeTreeChangeListener();
    for (int i = myContentManager.getContentCount() - 1; i >= 0; i--) {
      Content content = myContentManager.getContent(i);
      String id = content != null ? content.getUserData(ID_KEY) : null;
      if (id != null && id.equals(idToRemove)) {
        myContentManager.removeContent(content, true);
      }
    }
    myId2Pane.remove(idToRemove);
    mySelectInTargets.remove(idToRemove);
    viewSelectionChanged();
  }

  private synchronized void doAddUninitializedPanes() {
    for (AbstractProjectViewPane pane : myUninitializedPanes) {
      doAddPane(pane);
    }
    final Content[] contents = myContentManager.getContents();
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

    // try to find saved selected view...
    for (Content content : contents) {
      final String id = content.getUserData(ID_KEY);
      final String subId = content.getUserData(SUB_ID_KEY);
      if (id != null &&
          id.equals(mySavedPaneId) &&
          StringUtil.equals(subId, mySavedPaneSubId)) {
        selectID = id;
        selectSubID = subId;
        break;
      }
    }

    // saved view not found (plugin disabled, ID changed etc.) - select first available view...
    if (selectID == null && contents.length > 0) {
      Content content = contents[0];
      selectID = content.getUserData(ID_KEY);
      selectSubID = content.getUserData(SUB_ID_KEY);
    }

    if (selectID != null) {
      changeView(selectID, selectSubID);
      mySavedPaneId = null;
      mySavedPaneSubId = null;
    }

    myUninitializedPanes.clear();
  }

  private void doAddPane(@NotNull final AbstractProjectViewPane newPane) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int index;
    final ContentManager manager = myContentManager;
    for (index = 0; index < manager.getContentCount(); index++) {
      Content content = manager.getContent(index);
      String id = content.getUserData(ID_KEY);
      AbstractProjectViewPane pane = myId2Pane.get(id);

      int comp = PANE_WEIGHT_COMPARATOR.compare(pane, newPane);
      LOG.assertTrue(comp != 0, "Project view pane " + newPane + " has the same weight as " + pane +
                                ". Please make sure that you overload getWeight() and return a distinct weight value.");
      if (comp > 0) {
        break;
      }
    }
    final String id = newPane.getId();
    myId2Pane.put(id, newPane);
    String[] subIds = newPane.getSubIds();
    subIds = subIds.length == 0 ? new String[]{null} : subIds;
    boolean first = true;
    for (String subId : subIds) {
      final String title = subId != null ?  newPane.getPresentableSubIdName(subId) : newPane.getTitle();
      final Content content = myContentManager.getFactory().createContent(getComponent(), title, false);
      content.setTabName(title);
      content.putUserData(ID_KEY, id);
      content.putUserData(SUB_ID_KEY, subId);
      content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
      content.setIcon(newPane.getIcon());
      content.setPopupIcon(subId != null ? AllIcons.General.Bullet : newPane.getIcon());
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
    PsiElement selectedPsiElement = null;
    if (currentPane != null) {
      if (currentPane != newPane) {
        currentPane.saveExpandedPaths();
      }
      final PsiElement[] elements = currentPane.getSelectedPSIElements();
      if (elements.length > 0) {
        selectedPsiElement = elements[0];
      }
    }
    myViewContentPanel.removeAll();
    JComponent component = newPane.createComponent();
    UIUtil.removeScrollBorder(component);
    myViewContentPanel.setLayout(new BorderLayout());
    myViewContentPanel.add(component, BorderLayout.CENTER);
    myCurrentViewId = newPane.getId();
    String newSubId = myCurrentViewSubId = newPane.getSubId();
    myViewContentPanel.revalidate();
    myViewContentPanel.repaint();
    createToolbarActions();

    myAutoScrollToSourceHandler.install(newPane.myTree);

    IdeFocusManager.getInstance(myProject).requestFocus(newPane.getComponentToFocus(), false);

    newPane.restoreExpandedPaths();
    if (selectedPsiElement != null && newSubId != null) {
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(selectedPsiElement);
      if (virtualFile != null && ((ProjectViewSelectInTarget)newPane.createSelectInTarget()).isSubIdSelectable(newSubId, new SelectInContext() {
        @Override
        @NotNull
        public Project getProject() {
          return myProject;
        }

        @Override
        @NotNull
        public VirtualFile getVirtualFile() {
          return virtualFile;
        }

        @Override
        public Object getSelectorInFile() {
          return null;
        }

        @Override
        public FileEditorProvider getFileEditorProvider() {
          return null;
        }
      })) {
        newPane.select(selectedPsiElement, virtualFile, true);
      }
    }
    myAutoScrollToSourceHandler.onMouseClicked(newPane.myTree);
  }

  // public for tests
  public synchronized void setupImpl(@NotNull ToolWindow toolWindow) {
    setupImpl(toolWindow, true);
  }

  // public for tests
  public synchronized void setupImpl(@NotNull ToolWindow toolWindow, final boolean loadPaneExtensions) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myActionGroup = new DefaultActionGroup();

    myAutoScrollFromSourceHandler.install();

    myContentManager = toolWindow.getContentManager();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO);
      ((ToolWindowEx)toolWindow).setAdditionalGearActions(myActionGroup);
      toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
    SwingUtilities.invokeLater(() -> splitterProportions.restoreSplitterProportions(myPanel));

    if (loadPaneExtensions) {
      ensurePanesLoaded();
    }
    isInitialized = true;
    doAddUninitializedPanes();

    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          viewSelectionChanged();
        }
      }
    });
    viewSelectionChanged();
  }

  private void ensurePanesLoaded() {
    if (myExtensionsLoaded) return;
    myExtensionsLoaded = true;
    AbstractProjectViewPane[] extensions = Extensions.getExtensions(AbstractProjectViewPane.EP_NAME, myProject);
    Arrays.sort(extensions, PANE_WEIGHT_COMPARATOR);
    for(AbstractProjectViewPane pane: extensions) {
      if (myUninitializedPaneState.containsKey(pane.getId())) {
        try {
          pane.readExternal(myUninitializedPaneState.get(pane.getId()));
        }
        catch (InvalidDataException e) {
          // ignore
        }
        myUninitializedPaneState.remove(pane.getId());
      }
      if (pane.isInitiallyVisible() && !myId2Pane.containsKey(pane.getId())) {
        addProjectPane(pane);
      }
    }
  }

  private boolean viewSelectionChanged() {
    Content content = myContentManager.getSelectedContent();
    if (content == null) return false;
    final String id = content.getUserData(ID_KEY);
    String subId = content.getUserData(SUB_ID_KEY);
    if (content.equals(Pair.create(myCurrentViewId, myCurrentViewSubId))) return false;
    final AbstractProjectViewPane newPane = getProjectViewPaneById(id);
    if (newPane == null) return false;
    newPane.setSubId(subId);
    showPane(newPane);
    if (isAutoscrollFromSource(id)) {
      myAutoScrollFromSourceHandler.scrollFromSource();
    }
    return true;
  }

  private void createToolbarActions() {
    List<AnAction> titleActions = ContainerUtil.newSmartList();
    myActionGroup.removeAll();
    if (ProjectViewDirectoryHelper.getInstance(myProject).supportsFlattenPackages()) {
      myActionGroup.addAction(new PaneOptionAction(myFlattenPackages, IdeBundle.message("action.flatten.packages"),
                                             IdeBundle.message("action.flatten.packages"), PlatformIcons.FLATTEN_PACKAGES_ICON,
                                             ourFlattenPackagesDefaults) {
        @Override
        public void setSelected(AnActionEvent event, boolean flag) {
          final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
          final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

          super.setSelected(event, flag);

          selectionInfo.apply(viewPane);
        }
      }).setAsSecondary(true);
    }

    class FlattenPackagesDependableAction extends PaneOptionAction {
      FlattenPackagesDependableAction(@NotNull Map<String, Boolean> optionsMap,
                                      @NotNull String text,
                                      @NotNull String description,
                                      @NotNull Icon icon,
                                      boolean optionDefaultValue) {
        super(optionsMap, text, description, icon, optionDefaultValue);
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        presentation.setVisible(isFlattenPackages(myCurrentViewId));
      }
    }
    if (ProjectViewDirectoryHelper.getInstance(myProject).supportsHideEmptyMiddlePackages()) {
      myActionGroup.addAction(new HideEmptyMiddlePackagesAction()).setAsSecondary(true);
    }
    if (ProjectViewDirectoryHelper.getInstance(myProject).supportsFlattenPackages()) {
      myActionGroup.addAction(new FlattenPackagesDependableAction(myAbbreviatePackageNames,
                                                            IdeBundle.message("action.abbreviate.qualified.package.names"),
                                                            IdeBundle.message("action.abbreviate.qualified.package.names"),
                                                            AllIcons.ObjectBrowser.AbbreviatePackageNames,
                                                            ourAbbreviatePackagesDefaults) {
        @Override
        public boolean isSelected(AnActionEvent event) {
          return super.isSelected(event) && isAbbreviatePackageNames(myCurrentViewId);
        }


        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          if (ScopeViewPane.ID.equals(myCurrentViewId)) {
            e.getPresentation().setEnabled(false);
          }
        }
      }).setAsSecondary(true);
    }

    if (isShowMembersOptionSupported()) {
      myActionGroup.addAction(new PaneOptionAction(myShowMembers, IdeBundle.message("action.show.members"),
                                                   IdeBundle.message("action.show.hide.members"),
                                                   AllIcons.ObjectBrowser.ShowMembers, ourShowMembersDefaults))
        .setAsSecondary(true);
    }
    myActionGroup.addAction(myAutoScrollToSourceHandler.createToggleAction()).setAsSecondary(true);
    myActionGroup.addAction(myAutoScrollFromSourceHandler.createToggleAction()).setAsSecondary(true);
    myActionGroup.addAction(new ManualOrderAction()).setAsSecondary(true);
    myActionGroup.addAction(new SortByTypeAction()).setAsSecondary(true);
    myActionGroup.addAction(new FoldersAlwaysOnTopAction()).setAsSecondary(true);

    if (!myAutoScrollFromSourceHandler.isAutoScrollEnabled()) {
      titleActions.add(new ScrollFromSourceAction());
    }
    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(new TreeExpander() {
      @Override
      public void expandAll() {

      }

      @Override
      public boolean canExpand() {
        return false;
      }

      @Override
      public void collapseAll() {
        AbstractProjectViewPane pane = getCurrentProjectViewPane();
        JTree tree = pane.myTree;
        if (tree != null) {
          TreeUtil.collapseAll(tree, 0);
        }
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    }, getComponent());
    collapseAllAction.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);
    collapseAllAction.getTemplatePresentation().setHoveredIcon(AllIcons.General.CollapseAllHover);
    titleActions.add(collapseAllAction);
    getCurrentProjectViewPane().addToolbarActions(myActionGroup);

    ToolWindowEx window = (ToolWindowEx)ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (window != null) {
      window.setTitleActions(titleActions.toArray(new AnAction[titleActions.size()]));
    }
  }

  protected boolean isShowMembersOptionSupported() {
    return true;
  }

  @Override
  public AbstractProjectViewPane getProjectViewPaneById(String id) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {   // most tests don't need all panes to be loaded
      ensurePanesLoaded();
    }

    final AbstractProjectViewPane pane = myId2Pane.get(id);
    if (pane != null) {
      return pane;
    }
    for (AbstractProjectViewPane viewPane : myUninitializedPanes) {
      if (viewPane.getId().equals(id)) {
        return viewPane;
      }
    }
    return null;
  }

  @Override
  public AbstractProjectViewPane getCurrentProjectViewPane() {
    return getProjectViewPaneById(myCurrentViewId);
  }

  @Override
  public void refresh() {
    AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
    if (currentProjectViewPane != null) {
      // may be null for e.g. default project
      currentProjectViewPane.updateFromRoot(false);
    }
  }

  @Override
  public void select(final Object element, VirtualFile file, boolean requestFocus) {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null) {
      viewPane.select(element, file, requestFocus);
    }
  }

  @NotNull
  @Override
  public ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus) {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null && viewPane instanceof AbstractProjectViewPSIPane) {
      return ((AbstractProjectViewPSIPane)viewPane).selectCB(element, file, requestFocus);
    }
    select(element, file, requestFocus);
    return ActionCallback.DONE;
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
  }

  @Override
  public JComponent getComponent() {
    return myDataProvider;
  }

  @Override
  public String getCurrentViewId() {
    return myCurrentViewId;
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
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = node.getUserObject();
    if (userObject instanceof ProjectViewNode) {
      ProjectViewNode descriptor = (ProjectViewNode)userObject;
      Object element = descriptor.getValue();
      if (element instanceof PsiElement) {
        PsiElement psiElement = (PsiElement)element;
        if (!psiElement.isValid()) return null;
        return psiElement;
      }
      else {
        return null;
      }
    }
    return null;
  }


  private class PaneOptionAction extends ToggleAction implements DumbAware {
    private final Map<String, Boolean> myOptionsMap;
    private final boolean myOptionDefaultValue;

    PaneOptionAction(@NotNull Map<String, Boolean> optionsMap,
                     @NotNull String text,
                     @NotNull String description,
                     Icon icon,
                     boolean optionDefaultValue) {
      super(text, description, icon);
      myOptionsMap = optionsMap;
      myOptionDefaultValue = optionDefaultValue;
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return getPaneOptionValue(myOptionsMap, myCurrentViewId, myOptionDefaultValue);
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      setPaneOption(myOptionsMap, flag, myCurrentViewId, true);
    }
  }

  @Override
  public void changeView() {
    final List<AbstractProjectViewPane> views = new ArrayList<AbstractProjectViewPane>(myId2Pane.values());
    views.remove(getCurrentProjectViewPane());
    Collections.sort(views, PANE_WEIGHT_COMPARATOR);

    final JList list = new JBList(ArrayUtil.toObjectArray(views));
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        AbstractProjectViewPane pane = (AbstractProjectViewPane)value;
        setText(pane.getTitle());
        return this;
      }
    });

    if (!views.isEmpty()) {
      list.setSelectedValue(views.get(0), true);
    }
    Runnable runnable = () -> {
      if (list.getSelectedIndex() < 0) return;
      AbstractProjectViewPane pane = (AbstractProjectViewPane)list.getSelectedValue();
      changeView(pane.getId());
    };

    new PopupChooserBuilder(list).
      setTitle(IdeBundle.message("title.popup.views")).
      setItemChoosenCallback(runnable).
      createPopup().showInCenterOf(getComponent());
  }

  @Override
  public void changeView(@NotNull String viewId) {
    changeView(viewId, null);
  }

  @Override
  public void changeView(@NotNull String viewId, @Nullable String subId) {
    changeViewCB(viewId, subId);
  }

  @NotNull
  @Override
  public ActionCallback changeViewCB(@NotNull String viewId, String subId) {
    AbstractProjectViewPane pane = getProjectViewPaneById(viewId);
    LOG.assertTrue(pane != null, "Project view pane not found: " + viewId + "; subId:" + subId);
    if (!viewId.equals(getCurrentViewId())
        || subId != null && !subId.equals(pane.getSubId())) {
      for (Content content : myContentManager.getContents()) {
        if (viewId.equals(content.getUserData(ID_KEY)) && StringUtil.equals(subId, content.getUserData(SUB_ID_KEY))) {
          return myContentManager.setSelectedContentCB(content);
        }
      }
    }
    return ActionCallback.REJECTED;
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      final PsiElement[] elements = getElementsToDelete();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getElementsToDelete());
      List<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(validElements);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    @NotNull
    private PsiElement[] getElementsToDelete() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      PsiElement[] elements = viewPane.getSelectedPSIElements();
      for (int idx = 0; idx < elements.length; idx++) {
        final PsiElement element = elements[idx];
        if (element instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)element;
          final ProjectViewDirectoryHelper directoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);
          if (isHideEmptyMiddlePackages(viewPane.getId()) && directory.getChildren().length == 0 && !directoryHelper.skipDirectory(directory)) {
            while (true) {
              PsiDirectory parent = directory.getParentDirectory();
              if (parent == null) break;
              if (directoryHelper.skipDirectory(parent) || PsiDirectoryFactory.getInstance(myProject).getQualifiedName(parent, false).length() == 0) break;
              PsiElement[] children = parent.getChildren();
              if (children.length == 0 || children.length == 1 && children[0] == directory) {
                directory = parent;
              }
              else {
                break;
              }
            }
            elements[idx] = directory;
          }
          final VirtualFile virtualFile = directory.getVirtualFile();
          final String path = virtualFile.getPath();
          if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) { // if is jar-file root
            final VirtualFile vFile =
              LocalFileSystem.getInstance().findFileByPath(path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()));
            if (vFile != null) {
              final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
              if (psiFile != null) {
                elements[idx] = psiFile;
              }
            }
          }
        }
      }
      return elements;
    }

  }

  private final class MyPanel extends JPanel implements DataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    @Nullable
    private Object getSelectedNodeElement() {
      final AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
      if (currentProjectViewPane == null) { // can happen if not initialized yet
        return null;
      }
      DefaultMutableTreeNode node = currentProjectViewPane.getSelectedNode();
      if (node == null) {
        return null;
      }
      Object userObject = node.getUserObject();
      if (userObject instanceof AbstractTreeNode) {
        return ((AbstractTreeNode)userObject).getValue();
      }
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      return ((NodeDescriptor)userObject).getElement();
    }

    @Override
    public Object getData(String dataId) {
      final AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
      if (currentProjectViewPane != null) {
        final Object paneSpecificData = currentProjectViewPane.getData(dataId);
        if (paneSpecificData != null) return paneSpecificData;
      }

      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        if (currentProjectViewPane == null) return null;
        final PsiElement[] elements = currentProjectViewPane.getSelectedPSIElements();
        return elements.length == 1 ? elements[0] : null;
      }
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        if (currentProjectViewPane == null) {
          return null;
        }
        PsiElement[] elements = currentProjectViewPane.getSelectedPSIElements();
        return elements.length == 0 ? null : elements;
      }
      if (LangDataKeys.MODULE.is(dataId)) {
        VirtualFile[] virtualFiles = (VirtualFile[])getData(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName());
        if (virtualFiles == null || virtualFiles.length <= 1) return null;
        final Set<Module> modules = new HashSet<Module>();
        for (VirtualFile virtualFile : virtualFiles) {
          modules.add(ModuleUtilCore.findModuleForFile(virtualFile, myProject));
        }
        return modules.size() == 1 ? modules.iterator().next() : null;
      }
      if (LangDataKeys.TARGET_PSI_ELEMENT.is(dataId)) {
        return null;
      }
      if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
        return myCopyPasteDelegator.getCutProvider();
      }
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
        return myCopyPasteDelegator.getCopyProvider();
      }
      if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
        return myCopyPasteDelegator.getPasteProvider();
      }
      if (LangDataKeys.IDE_VIEW.is(dataId)) {
        return myIdeView;
      }
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        final Module[] modules = getSelectedModules();
        if (modules != null) {
          return myDeleteModuleProvider;
        }
        final LibraryOrderEntry orderEntry = getSelectedLibrary();
        if (orderEntry != null) {
          return new DeleteProvider() {
            @Override
            public void deleteElement(@NotNull DataContext dataContext) {
              detachLibrary(orderEntry, myProject);
            }

            @Override
            public boolean canDeleteElement(@NotNull DataContext dataContext) {
              return true;
            }
          };
        }
        return myDeletePSIElementProvider;
      }
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return HelpID.PROJECT_VIEWS;
      }
      if (ProjectViewImpl.DATA_KEY.is(dataId)) {
        return ProjectViewImpl.this;
      }
      if (PlatformDataKeys.PROJECT_CONTEXT.is(dataId)) {
        Object selected = getSelectedNodeElement();
        return selected instanceof Project ? selected : null;
      }
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
        Object selected = getSelectedNodeElement();
        if (selected instanceof Module) {
          return !((Module)selected).isDisposed() ? selected : null;
        }
        else if (selected instanceof PsiDirectory) {
          return moduleBySingleContentRoot(((PsiDirectory)selected).getVirtualFile());
        }
        else if (selected instanceof VirtualFile) {
          return moduleBySingleContentRoot((VirtualFile)selected);
        }
        else {
          return null;
        }
      }

      if (LangDataKeys.MODULE_CONTEXT_ARRAY.is(dataId)) {
        return getSelectedModules();
      }
      if (ModuleGroup.ARRAY_DATA_KEY.is(dataId)) {
        final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[selectedElements.size()]);
      }
      if (LibraryGroupElement.ARRAY_DATA_KEY.is(dataId)) {
        final List<LibraryGroupElement> selectedElements = getSelectedElements(LibraryGroupElement.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[selectedElements.size()]);
      }
      if (NamedLibraryElement.ARRAY_DATA_KEY.is(dataId)) {
        final List<NamedLibraryElement> selectedElements = getSelectedElements(NamedLibraryElement.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[selectedElements.size()]);
      }

      if (QuickActionProvider.KEY.is(dataId)) {
        return ProjectViewImpl.this;
      }

      return null;
    }

    @Nullable
    private LibraryOrderEntry getSelectedLibrary() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      DefaultMutableTreeNode node = viewPane != null ? viewPane.getSelectedNode() : null;
      if (node == null) return null;
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent == null) return null;
      Object userObject = parent.getUserObject();
      if (userObject instanceof LibraryGroupNode) {
        userObject = node.getUserObject();
        if (userObject instanceof NamedLibraryElementNode) {
          NamedLibraryElement element = ((NamedLibraryElementNode)userObject).getValue();
          OrderEntry orderEntry = element.getOrderEntry();
          return orderEntry instanceof LibraryOrderEntry ? (LibraryOrderEntry)orderEntry : null;
        }
        PsiDirectory directory = ((PsiDirectoryNode)userObject).getValue();
        VirtualFile virtualFile = directory.getVirtualFile();
        Module module = (Module)((AbstractTreeNode)((DefaultMutableTreeNode)parent.getParent()).getUserObject()).getValue();

        if (module == null) return null;
        ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
        OrderEntry entry = index.getOrderEntryForFile(virtualFile);
        if (entry instanceof LibraryOrderEntry) {
          return (LibraryOrderEntry)entry;
        }
      }

      return null;
    }

    private void detachLibrary(@NotNull final LibraryOrderEntry orderEntry, @NotNull Project project) {
      final Module module = orderEntry.getOwnerModule();
      String message = IdeBundle.message("detach.library.from.module", orderEntry.getPresentableName(), module.getName());
      String title = IdeBundle.message("detach.library");
      int ret = Messages.showOkCancelDialog(project, message, title, Messages.getQuestionIcon());
      if (ret != Messages.OK) return;
      CommandProcessor.getInstance().executeCommand(module.getProject(), () -> {
        final Runnable action = () -> {
          ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
          OrderEntry[] orderEntries = rootManager.getOrderEntries();
          ModifiableRootModel model = rootManager.getModifiableModel();
          OrderEntry[] modifiableEntries = model.getOrderEntries();
          for (int i = 0; i < orderEntries.length; i++) {
            OrderEntry entry = orderEntries[i];
            if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() == orderEntry.getLibrary()) {
              model.removeOrderEntry(modifiableEntries[i]);
            }
          }
          model.commit();
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }, title, null);
    }

    @Nullable
    private Module[] getSelectedModules() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane == null) return null;
      final Object[] elements = viewPane.getSelectedElements();
      ArrayList<Module> result = new ArrayList<Module>();
      for (Object element : elements) {
        if (element instanceof Module) {
          final Module module = (Module)element;
          if (!module.isDisposed()) {
            result.add(module);
          }
        }
        else if (element instanceof ModuleGroup) {
          Collection<Module> modules = ((ModuleGroup)element).modulesInGroup(myProject, true);
          result.addAll(modules);
        }
        else if (element instanceof PsiDirectory) {
          Module module = moduleBySingleContentRoot(((PsiDirectory)element).getVirtualFile());
          if (module != null) result.add(module);
        }
        else if (element instanceof VirtualFile) {
          Module module = moduleBySingleContentRoot((VirtualFile)element);
          if (module != null) result.add(module);
        }
      }

      if (result.isEmpty()) {
        return null;
      }
      else {
        return result.toArray(new Module[result.size()]);
      }
    }
  }

  /** Project view has the same node for module and its single content root 
   *   => MODULE_CONTEXT data key should return the module when its content root is selected
   *  When there are multiple content roots, they have different nodes under the module node
   *   => MODULE_CONTEXT should be only available for the module node
   *      otherwise VirtualFileArrayRule will return all module's content roots when just one of them is selected
   */
  @Nullable
  private Module moduleBySingleContentRoot(@NotNull VirtualFile file) {
    if (ProjectRootsUtil.isModuleContentRoot(file, myProject)) {
      Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
      if (module != null && !module.isDisposed() && ModuleRootManager.getInstance(module).getContentRoots().length == 1) {
        return module;
      }
    }

    return null;
  }

  @NotNull
  private <T> List<T> getSelectedElements(@NotNull Class<T> klass) {
    List<T> result = new ArrayList<T>();
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane == null) return result;
    final Object[] elements = viewPane.getSelectedElements();
    for (Object element : elements) {
      //element still valid
      if (element != null && klass.isAssignableFrom(element.getClass())) {
        result.add((T)element);
      }
    }
    return result;
  }

  private final class MyIdeView implements IdeView {
    @Override
    public void selectElement(PsiElement element) {
      selectPsiElement(element, false);
      boolean requestFocus = true;
      if (element != null) {
        final boolean isDirectory = element instanceof PsiDirectory;
        if (!isDirectory) {
          FileEditor editor = EditorHelper.openInEditor(element, false);
          if (editor != null) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
            requestFocus = false;
          }
        }
      }

      if (requestFocus) {
        selectPsiElement(element, true);
      }
    }

    @NotNull
    @Override
    public PsiDirectory[] getDirectories() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane != null) {
        return viewPane.getSelectedDirectories();
      }

      return PsiDirectory.EMPTY_ARRAY;
    }

    @Override
    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }

  @Override
  public void selectPsiElement(PsiElement element, boolean requestFocus) {
    if (element == null) return;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    select(element, virtualFile, requestFocus);
  }


  private static void readOption(Element node, @NotNull Map<String, Boolean> options) {
    if (node == null) return;
    for (Attribute attribute : node.getAttributes()) {
      options.put(attribute.getName(), Boolean.TRUE.toString().equals(attribute.getValue()) ? Boolean.TRUE : Boolean.FALSE);
    }
  }

  private static void writeOption(@NotNull Element parentNode, @NotNull Map<String, Boolean> optionsForPanes, @NotNull String optionName) {
    Element e = new Element(optionName);
    for (Map.Entry<String, Boolean> entry : optionsForPanes.entrySet()) {
      final String key = entry.getKey();
      if (key != null) { //SCR48267
        e.setAttribute(key, Boolean.toString(entry.getValue().booleanValue()));
      }
    }

    parentNode.addContent(e);
  }

  @Override
  public void loadState(Element parentNode) {
    Element navigatorElement = parentNode.getChild(ELEMENT_NAVIGATOR);
    if (navigatorElement != null) {
      mySavedPaneId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_VIEW);
      mySavedPaneSubId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_SUBVIEW);
      if (mySavedPaneId == null) {
        mySavedPaneId = ProjectViewPane.ID;
        mySavedPaneSubId = null;
      }
      readOption(navigatorElement.getChild(ELEMENT_FLATTEN_PACKAGES), myFlattenPackages);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_MEMBERS), myShowMembers);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_MODULES), myShowModules);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_LIBRARY_CONTENTS), myShowLibraryContents);
      readOption(navigatorElement.getChild(ELEMENT_HIDE_EMPTY_PACKAGES), myHideEmptyPackages);
      readOption(navigatorElement.getChild(ELEMENT_ABBREVIATE_PACKAGE_NAMES), myAbbreviatePackageNames);
      readOption(navigatorElement.getChild(ELEMENT_AUTOSCROLL_TO_SOURCE), myAutoscrollToSource);
      readOption(navigatorElement.getChild(ELEMENT_AUTOSCROLL_FROM_SOURCE), myAutoscrollFromSource);
      readOption(navigatorElement.getChild(ELEMENT_SORT_BY_TYPE), mySortByType);
      readOption(navigatorElement.getChild(ELEMENT_MANUAL_ORDER), myManualOrder);

      Element foldersElement = navigatorElement.getChild(ELEMENT_FOLDERS_ALWAYS_ON_TOP);
      if (foldersElement != null) myFoldersAlwaysOnTop = Boolean.valueOf(foldersElement.getAttributeValue("value"));
      
      try {
        splitterProportions.readExternal(navigatorElement);
      }
      catch (InvalidDataException e) {
        // ignore
      }
    }
    Element panesElement = parentNode.getChild(ELEMENT_PANES);
    if (panesElement != null) {
      readPaneState(panesElement);
    }
  }

  private void readPaneState(@NotNull Element panesElement) {
    @SuppressWarnings({"unchecked"})
    final List<Element> paneElements = panesElement.getChildren(ELEMENT_PANE);

    for (Element paneElement : paneElements) {
      String paneId = paneElement.getAttributeValue(ATTRIBUTE_ID);
      final AbstractProjectViewPane pane = myId2Pane.get(paneId);
      if (pane != null) {
        try {
          pane.readExternal(paneElement);
        }
        catch (InvalidDataException e) {
          // ignore
        }
      }
      else {
        myUninitializedPaneState.put(paneId, paneElement);
      }
    }
  }

  @Override
  public Element getState() {
    Element parentNode = new Element("projectView");
    Element navigatorElement = new Element(ELEMENT_NAVIGATOR);
    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    if (currentPane != null) {
      navigatorElement.setAttribute(ATTRIBUTE_CURRENT_VIEW, currentPane.getId());
      String subId = currentPane.getSubId();
      if (subId != null) {
        navigatorElement.setAttribute(ATTRIBUTE_CURRENT_SUBVIEW, subId);
      }
    }
    writeOption(navigatorElement, myFlattenPackages, ELEMENT_FLATTEN_PACKAGES);
    writeOption(navigatorElement, myShowMembers, ELEMENT_SHOW_MEMBERS);
    writeOption(navigatorElement, myShowModules, ELEMENT_SHOW_MODULES);
    writeOption(navigatorElement, myShowLibraryContents, ELEMENT_SHOW_LIBRARY_CONTENTS);
    writeOption(navigatorElement, myHideEmptyPackages, ELEMENT_HIDE_EMPTY_PACKAGES);
    writeOption(navigatorElement, myAbbreviatePackageNames, ELEMENT_ABBREVIATE_PACKAGE_NAMES);
    writeOption(navigatorElement, myAutoscrollToSource, ELEMENT_AUTOSCROLL_TO_SOURCE);
    writeOption(navigatorElement, myAutoscrollFromSource, ELEMENT_AUTOSCROLL_FROM_SOURCE);
    writeOption(navigatorElement, mySortByType, ELEMENT_SORT_BY_TYPE);
    writeOption(navigatorElement, myManualOrder, ELEMENT_MANUAL_ORDER);
    
    Element foldersElement = new Element(ELEMENT_FOLDERS_ALWAYS_ON_TOP);
    foldersElement.setAttribute("value", Boolean.toString(myFoldersAlwaysOnTop));
    navigatorElement.addContent(foldersElement);

    splitterProportions.saveSplitterProportions(myPanel);
    try {
      splitterProportions.writeExternal(navigatorElement);
    }
    catch (WriteExternalException e) {
      // ignore
    }
    parentNode.addContent(navigatorElement);

    Element panesElement = new Element(ELEMENT_PANES);
    writePaneState(panesElement);
    parentNode.addContent(panesElement);
    return parentNode;
  }

  private void writePaneState(@NotNull Element panesElement) {
    for (AbstractProjectViewPane pane : myId2Pane.values()) {
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

  @Override
  public boolean isAutoscrollToSource(String paneId) {
    return getPaneOptionValue(myAutoscrollToSource, paneId, UISettings.getInstance().DEFAULT_AUTOSCROLL_TO_SOURCE);
  }

  public void setAutoscrollToSource(boolean autoscrollMode, String paneId) {
    myAutoscrollToSource.put(paneId, autoscrollMode);
  }

  @Override
  public boolean isAutoscrollFromSource(String paneId) {
    return getPaneOptionValue(myAutoscrollFromSource, paneId, ourAutoscrollFromSourceDefaults);
  }

  public void setAutoscrollFromSource(boolean autoscrollMode, String paneId) {
    setPaneOption(myAutoscrollFromSource, autoscrollMode, paneId, false);
  }

  @Override
  public boolean isFlattenPackages(String paneId) {
    return getPaneOptionValue(myFlattenPackages, paneId, ourFlattenPackagesDefaults);
  }

  public void setFlattenPackages(boolean flattenPackages, String paneId) {
    setPaneOption(myFlattenPackages, flattenPackages, paneId, true);
  }

  public boolean isFoldersAlwaysOnTop() {
    return myFoldersAlwaysOnTop;
  }

  public void setFoldersAlwaysOnTop(boolean foldersAlwaysOnTop) {
    if (myFoldersAlwaysOnTop != foldersAlwaysOnTop) {
      myFoldersAlwaysOnTop = foldersAlwaysOnTop;
      for (AbstractProjectViewPane pane : myId2Pane.values()) {
        if (pane.getTree() != null) {
          pane.updateFromRoot(false);
        }
      }
    }
  }

  @Override
  public boolean isShowMembers(String paneId) {
    return getPaneOptionValue(myShowMembers, paneId, ourShowMembersDefaults);
  }

  public void setShowMembers(boolean showMembers, String paneId) {
    setPaneOption(myShowMembers, showMembers, paneId, true);
  }

  @Override
  public boolean isHideEmptyMiddlePackages(String paneId) {
    return getPaneOptionValue(myHideEmptyPackages, paneId, ourHideEmptyPackagesDefaults);
  }

  @Override
  public boolean isAbbreviatePackageNames(String paneId) {
    return getPaneOptionValue(myAbbreviatePackageNames, paneId, ourAbbreviatePackagesDefaults);
  }

  @Override
  public boolean isShowLibraryContents(String paneId) {
    return getPaneOptionValue(myShowLibraryContents, paneId, ourShowLibraryContentsDefaults);
  }

  @Override
  public void setShowLibraryContents(boolean showLibraryContents, @NotNull String paneId) {
    setPaneOption(myShowLibraryContents, showLibraryContents, paneId, true);
  }

  @NotNull
  public ActionCallback setShowLibraryContentsCB(boolean showLibraryContents, String paneId) {
    return setPaneOption(myShowLibraryContents, showLibraryContents, paneId, true);
  }

  @Override
  public boolean isShowModules(String paneId) {
    return getPaneOptionValue(myShowModules, paneId, ourShowModulesDefaults);
  }

  @Override
  public void setShowModules(boolean showModules, @NotNull String paneId) {
    setPaneOption(myShowModules, showModules, paneId, true);
  }

  @Override
  public void setHideEmptyPackages(boolean hideEmptyPackages, @NotNull String paneId) {
    setPaneOption(myHideEmptyPackages, hideEmptyPackages, paneId, true);
  }

  @Override
  public void setAbbreviatePackageNames(boolean abbreviatePackageNames, @NotNull String paneId) {
    setPaneOption(myAbbreviatePackageNames, abbreviatePackageNames, paneId, true);
  }

  @NotNull
  private ActionCallback setPaneOption(@NotNull Map<String, Boolean> optionsMap, boolean value, String paneId, final boolean updatePane) {
    optionsMap.put(paneId, value);
    if (updatePane) {
      final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
      if (pane != null) {
        return pane.updateFromRoot(false);
      }
    }
    return ActionCallback.DONE;
  }

  private static boolean getPaneOptionValue(@NotNull Map<String, Boolean> optionsMap, String paneId, boolean defaultValue) {
    final Boolean value = optionsMap.get(paneId);
    return value == null ? defaultValue : value.booleanValue();
  }

  private class HideEmptyMiddlePackagesAction extends PaneOptionAction {
    private HideEmptyMiddlePackagesAction() {
      super(myHideEmptyPackages, "", "", null, ourHideEmptyPackagesDefaults);
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

      super.setSelected(event, flag);

      selectionInfo.apply(viewPane);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      if (isFlattenPackages(myCurrentViewId)) {
        presentation.setText(IdeBundle.message("action.hide.empty.middle.packages"));
        presentation.setDescription(IdeBundle.message("action.show.hide.empty.middle.packages"));
      }
      else {
        presentation.setText(IdeBundle.message("action.compact.empty.middle.packages"));
        presentation.setDescription(IdeBundle.message("action.show.compact.empty.middle.packages"));
      }
    }
  }

  private static class SelectionInfo {
    private final Object[] myElements;

    private SelectionInfo(@NotNull Object[] elements) {
      myElements = elements;
    }

    public void apply(final AbstractProjectViewPane viewPane) {
      if (viewPane == null) {
        return;
      }
      AbstractTreeBuilder treeBuilder = viewPane.getTreeBuilder();
      JTree tree = viewPane.myTree;
      DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
      List<TreePath> paths = new ArrayList<TreePath>(myElements.length);
      for (final Object element : myElements) {
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node == null) {
          treeBuilder.buildNodeForElement(element);
          node = treeBuilder.getNodeForElement(element);
        }
        if (node != null) {
          paths.add(new TreePath(treeModel.getPathToRoot(node)));
        }
      }
      if (!paths.isEmpty()) {
        tree.setSelectionPaths(paths.toArray(new TreePath[paths.size()]));
      }
    }

    @NotNull
    public static SelectionInfo create(final AbstractProjectViewPane viewPane) {
      List<Object> selectedElements = Collections.emptyList();
      if (viewPane != null) {
        final TreePath[] selectionPaths = viewPane.getSelectionPaths();
        if (selectionPaths != null) {
          selectedElements = new ArrayList<Object>();
          for (TreePath path : selectionPaths) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            final Object userObject = node.getUserObject();
            if (userObject instanceof NodeDescriptor) {
              selectedElements.add(((NodeDescriptor)userObject).getElement());
            }
          }
        }
      }
      return new SelectionInfo(selectedElements.toArray());
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private MyAutoScrollFromSourceHandler() {
      super(ProjectViewImpl.this.myProject, myViewContentPanel, ProjectViewImpl.this);
    }

    @Override
    protected void selectElementFromEditor(@NotNull FileEditor fileEditor) {
      if (myProject.isDisposed() || !myViewContentPanel.isShowing()) return;
      if (isAutoscrollFromSource(getCurrentViewId())) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          selectElementAtCaretNotLosingFocus(editor);
        }
        else {
          final VirtualFile file = FileEditorManagerEx.getInstanceEx(myProject).getFile(fileEditor);
          if (file != null) {
            final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
            if (psiFile != null) {
              final SelectInTarget target = mySelectInTargets.get(getCurrentViewId());
              if (target != null) {
                final MySelectInContext selectInContext = new MySelectInContext(psiFile, null) {
                  @Override
                  public Object getSelectorInFile() {
                    return psiFile;
                  }
                };

                if (target.canSelect(selectInContext)) {
                  target.selectIn(selectInContext, false);
                }
              }
            }
          }
        }
      }
    }

    public void scrollFromSource() {
      final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
      final Editor selectedTextEditor = fileEditorManager.getSelectedTextEditor();
      if (selectedTextEditor != null) {
        selectElementAtCaret(selectedTextEditor);
        return;
      }
      final FileEditor[] editors = fileEditorManager.getSelectedEditors();
      for (FileEditor fileEditor : editors) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          selectElementAtCaret(editor);
          return;
        }
      }
      final VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
      if (selectedFiles.length > 0) {
        final PsiFile file = PsiManager.getInstance(myProject).findFile(selectedFiles[0]);
        if (file != null) {
          scrollFromFile(file, null);
        }
      }
    }

    private void selectElementAtCaretNotLosingFocus(@NotNull Editor editor) {
      if (IJSwingUtilities.hasFocus(getCurrentProjectViewPane().getComponentToFocus())) return;
      selectElementAtCaret(editor);
    }

    private void selectElementAtCaret(@NotNull Editor editor) {
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;

      scrollFromFile(file, editor);
    }

    private void scrollFromFile(@NotNull PsiFile file, @Nullable Editor editor) {
      final MySelectInContext selectInContext = new MySelectInContext(file, editor);

      final SelectInTarget target = mySelectInTargets.get(getCurrentViewId());
      if (target != null && target.canSelect(selectInContext)) {
        target.selectIn(selectInContext, false);
      }
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return isAutoscrollFromSource(myCurrentViewId);
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      setAutoscrollFromSource(state, myCurrentViewId);
      if (state) {
        final Editor editor = myFileEditorManager.getSelectedTextEditor();
        if (editor != null) {
          selectElementAtCaretNotLosingFocus(editor);
        }
      }
      createToolbarActions();
    }

    private class MySelectInContext implements SelectInContext {
      @NotNull private final PsiFile myPsiFile;
      @Nullable private final Editor myEditor;

      private MySelectInContext(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        myPsiFile = psiFile;
        myEditor = editor;
      }

      @Override
      @NotNull
      public Project getProject() {
        return myProject;
      }

      @NotNull
      private PsiFile getPsiFile() {
        return myPsiFile;
      }

      @Override
      @NotNull
      public FileEditorProvider getFileEditorProvider() {
        return new FileEditorProvider() {
          @Override
          public FileEditor openFileEditor() {
            return myFileEditorManager.openFile(myPsiFile.getContainingFile().getVirtualFile(), false)[0];
          }
        };
      }

      @NotNull
      private PsiElement getPsiElement() {
        PsiElement e = null;
        if (myEditor != null) {
          final int offset = myEditor.getCaretModel().getOffset();
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          e = getPsiFile().findElementAt(offset);
        }
        if (e == null) {
          e = getPsiFile();
        }
        return e;
      }

      @Override
      @NotNull
      public VirtualFile getVirtualFile() {
        return getPsiFile().getVirtualFile();
      }

      @Override
      public Object getSelectorInFile() {
        return getPsiElement();
      }
    }
  }

  @Override
  public boolean isManualOrder(String paneId) {
    return getPaneOptionValue(myManualOrder, paneId, ourManualOrderDefaults);
  }

  @Override
  public void setManualOrder(@NotNull String paneId, final boolean enabled) {
    setPaneOption(myManualOrder, enabled, paneId, false);
    final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
    pane.installComparator();
  }

  @Override
  public boolean isSortByType(String paneId) {
    return getPaneOptionValue(mySortByType, paneId, ourSortByTypeDefaults);
  }

  @Override
  public void setSortByType(@NotNull String paneId, final boolean sortByType) {
    setPaneOption(mySortByType, sortByType, paneId, false);
    final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
    pane.installComparator();
  }

  private class ManualOrderAction extends ToggleAction implements DumbAware {
    private ManualOrderAction() {
      super(IdeBundle.message("action.manual.order"), IdeBundle.message("action.manual.order"), AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return isManualOrder(getCurrentViewId());
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      setManualOrder(getCurrentViewId(), flag);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      AbstractProjectViewPane pane = getCurrentProjectViewPane();
      presentation.setEnabledAndVisible(pane != null && pane.supportsManualOrder());
    }
  }
  
  private class SortByTypeAction extends ToggleAction implements DumbAware {
    private SortByTypeAction() {
      super(IdeBundle.message("action.sort.by.type"), IdeBundle.message("action.sort.by.type"), AllIcons.ObjectBrowser.SortByType);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return isSortByType(getCurrentViewId());
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      setSortByType(getCurrentViewId(), flag);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setVisible(getCurrentProjectViewPane() != null);
    }
  }

  private class FoldersAlwaysOnTopAction extends ToggleAction implements DumbAware {
    private FoldersAlwaysOnTopAction() {
      super("Folders Always on Top");
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return isFoldersAlwaysOnTop();
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      setFoldersAlwaysOnTop(flag);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(getCurrentProjectViewPane() != null);
    }
  }

  private class ScrollFromSourceAction extends AnAction implements DumbAware {
    private ScrollFromSourceAction() {
      super("Scroll from Source", "Select the file open in the active editor", AllIcons.General.Locate);
      getTemplatePresentation().setHoveredIcon(AllIcons.General.LocateHover);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myAutoScrollFromSourceHandler.scrollFromSource();
    }
  }

  @NotNull
  @Override
  public Collection<String> getPaneIds() {
    return myId2Pane.keySet();
  }

  @NotNull
  @Override
  public Collection<SelectInTarget> getSelectInTargets() {
    ensurePanesLoaded();
    return mySelectInTargets.values();
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    AbstractProjectViewPane pane = myId2Pane.get(myCurrentViewSubId);
    if (pane == null) {
      pane = myId2Pane.get(myCurrentViewId);
    }
    return pane != null ? pane.getReady(requestor) : ActionCallback.DONE;
  }
}
