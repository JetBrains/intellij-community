/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.*;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.HelpID;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.switcher.QuickAccessProvider;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Icons;
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
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.*;
import java.util.List;

@State(
  name="ProjectView",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public final class ProjectViewImpl extends ProjectView implements PersistentStateComponent<Element>, Disposable, QuickActionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.ProjectViewImpl");
  private final CopyPasteDelegator myCopyPasteDelegator;
  private boolean isInitialized;
  private boolean myExtensionsLoaded = false;
  private final Project myProject;

  // + options
  private final Map<String, Boolean> myFlattenPackages = new THashMap<String, Boolean>();
  private static final boolean ourFlattenPackagesDefaults = false;
  private final Map<String, Boolean> myShowMembers = new THashMap<String, Boolean>();
  private static final boolean ourShowMembersDefaults = false;
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
  private static final boolean ourAutoscrollToSourceDefaults = false;
  private final Map<String, Boolean> myAutoscrollFromSource = new THashMap<String, Boolean>();
  private static final boolean ourAutoscrollFromSourceDefaults = false;
  private static final boolean ourShowStructureDefaults = false;

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
  @Deprecated static final String PROJECT_VIEW_DATA_CONSTANT = DATA_KEY.getName();

  private DefaultActionGroup myActionGroup;
  private final Runnable myTreeChangeListener;
  private String mySavedPaneId = ProjectViewPane.ID;
  private String mySavedPaneSubId;
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");
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
  private static final String ATTRIBUTE_ID = "id";
  private ComboBox myCombo;
  private JPanel myViewContentPanel;
  private JPanel myActionGroupPanel;
  private JLabel myLabel;
  private static final Comparator<AbstractProjectViewPane> PANE_WEIGHT_COMPARATOR = new Comparator<AbstractProjectViewPane>() {
    public int compare(final AbstractProjectViewPane o1, final AbstractProjectViewPane o2) {
      return o1.getWeight() - o2.getWeight();
    }
  };
  private final FileEditorManager myFileEditorManager;
  private final MyPanel myDataProvider;
  private final SplitterProportionsData splitterProportions = new SplitterProportionsDataImpl();
  private static final Icon BULLET_ICON = IconLoader.getIcon("/general/bullet.png");
  private final MessageBusConnection myConnection;
  private JPanel myTopPanel;
  private ActionToolbar myToolBar;
  private final Map<String, Element> myUninitializedPaneState = new HashMap<String, Element>();
  private final Map<String, SelectInTarget> mySelectInTargets = new HashMap<String, SelectInTarget>();

  public ProjectViewImpl(Project project, final FileEditorManager fileEditorManager, final ToolWindowManagerEx toolWindowManager) {
    myProject = project;

    constructUi();


    Disposer.register(myProject, this);
    myFileEditorManager = fileEditorManager;
    myTreeChangeListener = new Runnable() {
      public void run() {
        updateToolWindowTitle();
      }
    };

    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        refresh();
      }
    });

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();

    myDataProvider = new MyPanel();
    myDataProvider.add(myPanel, BorderLayout.CENTER);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, myPanel) {
      @NotNull
      protected PsiElement[] getSelectedElements() {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        return viewPane == null ? PsiElement.EMPTY_ARRAY : viewPane.getSelectedPSIElements();
      }
    };
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
    protected boolean isAutoScrollMode() {
      return isAutoscrollToSource(myCurrentViewId);
    }

    protected void setAutoScrollMode(boolean state) {
      setAutoscrollToSource(state, myCurrentViewId);
    }
  };
    toolWindowManager.addToolWindowManagerListener(new ToolWindowManagerAdapter(){
      private boolean toolWindowVisible;

      public void stateChanged() {
        ToolWindow window = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (window == null) return;
        if (window.isVisible() && !toolWindowVisible) {
          String id = getCurrentViewId();
          if (isAutoscrollToSource(id)) {
            myAutoScrollToSourceHandler.onMouseClicked(getCurrentProjectViewPane().getTree());
          }
          if (isAutoscrollFromSource(id)) {
            myAutoScrollFromSourceHandler.setAutoScrollMode(true);
          }
        }
        toolWindowVisible = window.isVisible();
      }
    });
  }

  private void constructUi() {
    myActionGroupPanel = new JPanel(new BorderLayout());

    myLabel = new JLabel("View as:");
    if (!SystemInfo.isMac) { // See IDEADEV-41315
      myLabel.setDisplayedMnemonic('a');
    }
    myCombo = new ComboBox();
    myCombo.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    myLabel.setLabelFor(myCombo);

    final JPanel combo = new JPanel(new BorderLayout());
    combo.setBorder(new EmptyBorder(4, 4, 4, 4));
    combo.add(myLabel, BorderLayout.WEST);
    combo.add(myCombo, BorderLayout.CENTER);


    myTopPanel = new JPanel(new GridBagLayout());
    myTopPanel.add(combo, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myTopPanel.add(myActionGroupPanel, new GridBagConstraints(1, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    myViewContentPanel = new JPanel();
    myPanel = new SimpleToolWindowPanel(true).setProvideQuickActions(false);
    myPanel.setToolbar(myTopPanel);
    myPanel.setContent(myViewContentPanel);

    myPanel.setBorder(new ToolWindow.Border(true, false, false, false));
  }

  public String getName() {
    return "Project";
  }

  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();

    DefaultActionGroup views = new DefaultActionGroup("Change View", true);
    boolean lastWasHeader = false;
    boolean lastHeaderHadKids = false;
    for (int i = 0; i < myCombo.getModel().getSize(); i++) {
      Object each = myCombo.getModel().getElementAt(i);
      if (each instanceof Pair) {
        Pair<String, String> eachPair = (Pair<String, String>)each;

        if (eachPair.getSecond() == null) {
          if (lastHeaderHadKids) {
            views.add(new Separator());
          } else {
            if (i + 1 < myCombo.getModel().getSize()) {
              Object next = myCombo.getModel().getElementAt(i + 1);
              if (next instanceof Pair) {
                if (((Pair)next).getSecond() != null) {
                  views.add(new Separator());
                }
              }
            }
          }
        } else {
          lastHeaderHadKids = true;
        }

        lastWasHeader = eachPair.getSecond() == null;

        views.add(new ChangeViewAction(eachPair.getFirst(), eachPair.getSecond()));
      }
    }
    result.add(views);
    result.add(new Separator());


    ArrayList<AnAction> secondary = new ArrayList<AnAction>();
    if (myActionGroup != null) {
      AnAction[] kids = myActionGroup.getChildren(null);
      for (AnAction each : kids) {
        if (myActionGroup.isPrimary(each)) {
          result.add(each);
        } else {
          secondary.add(each);
        }
      }
    }
    result.add(new Separator());
    result.addAll(secondary);

    return result;
  }

  private class ChangeViewAction extends AnAction {
    private String myId;
    private String mySubId;

    private ChangeViewAction(String id, String subId) {
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

  public boolean isCycleRoot() {
    return false;
  }

  public synchronized void addProjectPane(final AbstractProjectViewPane pane) {
    myUninitializedPanes.add(pane);
    SelectInTarget selectInTarget = pane.createSelectInTarget();
    if (selectInTarget != null) {
      mySelectInTargets.put(pane.getId(), selectInTarget);
    }                                   
    if (isInitialized) {
      doAddUninitializedPanes();
    }
  }

  public synchronized void removeProjectPane(AbstractProjectViewPane pane) {
    myUninitializedPanes.remove(pane);
    //assume we are completely initialized here
    String idToRemove = pane.getId();

    if (!myId2Pane.containsKey(idToRemove)) return;
    pane.removeTreeChangeListener();
    for (int i = myCombo.getItemCount() - 1; i >= 0; i--) {
      Pair<String, String> ids = (Pair<String, String>)myCombo.getItemAt(i);
      String id = ids.first;
      if (id.equals(idToRemove)) {
        if (i == myCombo.getSelectedIndex()) {
          myCombo.setSelectedIndex(0);
        }
        myCombo.removeItemAt(i);
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
    if (myCombo.getSelectedItem() == null) { //old selection isn't available anymore
      final DefaultComboBoxModel comboBoxModel = (DefaultComboBoxModel)myCombo.getModel();
      final int size = comboBoxModel.getSize();
      if (size > 0) {
        final Pair<String, String> ids = (Pair<String, String>)comboBoxModel.getElementAt(size - 1);
        changeView(ids.first, ids.second);
      }
    }
    myUninitializedPanes.clear();
  }

  private void doAddPane(final AbstractProjectViewPane newPane) {
    int index;
    for (index = 0; index < myCombo.getItemCount(); index++) {
      Pair<String, String> ids = (Pair<String, String>)myCombo.getItemAt(index);
      String id = ids.first;
      AbstractProjectViewPane pane = myId2Pane.get(id);

      int comp = PANE_WEIGHT_COMPARATOR.compare(pane, newPane);
      if (comp == 0) {
        System.out.println("here");
      }
      LOG.assertTrue(comp != 0);
      if (comp > 0) {
        break;
      }
    }
    final String id = newPane.getId();
    myId2Pane.put(id, newPane);
    String[] subIds = newPane.getSubIds();
    subIds = ArrayUtil.mergeArrays(new String[]{null}, subIds, String.class);
    for (String subId : subIds) {
      myCombo.insertItemAt(Pair.create(id, subId), index++);
    }
    myCombo.setMaximumRowCount(myCombo.getItemCount());

    if (id.equals(mySavedPaneId)) {
      changeView(mySavedPaneId, mySavedPaneSubId);
      mySavedPaneId = null;
      mySavedPaneSubId = null;
    }

    Disposer.register(this, newPane);
  }

  private void showPane(AbstractProjectViewPane newPane) {
    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    PsiElement selectedPsiElement = null;
    Module selectedModule = null;
    if (currentPane != null) {
      if (currentPane != newPane) {
        currentPane.saveExpandedPaths();
      }
      final PsiElement[] elements = currentPane.getSelectedPSIElements();
      if (elements.length > 0) {
        selectedPsiElement = elements[0];
      } else {
        Object selected = currentPane.getSelectedElement();
        if (selected instanceof Module) {
          selectedModule = (Module)selected;
        }
      }
    }
    removeLabelFocusListener();
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
    myToolBar.updateActionsImmediately();
    myTopPanel.revalidate();

    newPane.setTreeChangeListener(myTreeChangeListener);
    myAutoScrollToSourceHandler.install(newPane.myTree);

    IdeFocusManager.getInstance(myProject).requestFocus(newPane.getComponentToFocus(), false);
    updateToolWindowTitle();

    newPane.restoreExpandedPaths();
    if (selectedPsiElement != null) {
      final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(selectedPsiElement);
      if (virtualFile != null && ((ProjectViewSelectInTarget)newPane.createSelectInTarget()).isSubIdSelectable(newSubId, new SelectInContext() {
        @NotNull
        public Project getProject() {
          return myProject;
        }

        @NotNull
        public VirtualFile getVirtualFile() {
          return virtualFile;
        }

        public Object getSelectorInFile() {
          return null;
        }

        public FileEditorProvider getFileEditorProvider() {
          return null;
        }
      })) {
        newPane.select(selectedPsiElement, virtualFile, true);
      }
    }
    myAutoScrollToSourceHandler.onMouseClicked(newPane.myTree);
    installLabelFocusListener();
  }

  // public for tests
  public synchronized void setupImpl(final ToolWindow toolWindow) {
    setupImpl(toolWindow, true);
  }

  // public for tests
  public synchronized void setupImpl(final ToolWindow toolWindow, final boolean loadPaneExtensions) {
    myCombo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) return this;
        Pair<String, String> ids = (Pair<String, String>)value;
        String id = ids.first;
        String subId = ids.second;
        AbstractProjectViewPane pane = getProjectViewPaneById(id);
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (pane != null) {
          if (subId == null) {
            setText(pane.getTitle());
            setIcon(pane.getIcon());
          }
          else {
            String presentable = pane.getPresentableSubIdName(subId);
            if (index == -1) {
              setText(presentable);
              setIcon(pane.getIcon());
            }
            else {
              // indent sub id
              setText(presentable);
              setIcon(BULLET_ICON);
            }
          }
        }
        return this;
      }
    });
    myCombo.setMinimumAndPreferredWidth(10);

    myActionGroup = new DefaultActionGroup();

    myAutoScrollFromSourceHandler.install();

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, myActionGroup, true);
    myToolBar.setSecondaryActionsTooltip("View Options");
    JComponent toolbarComponent = myToolBar.getComponent();
    myActionGroupPanel.setLayout(new BorderLayout());
    myActionGroupPanel.add(toolbarComponent, BorderLayout.CENTER);

    if (toolWindow != null) {
      final ContentManager contentManager = toolWindow.getContentManager();
      final Content content = contentManager.getFactory().createContent(getComponent(), ToolWindowId.PROJECT_VIEW, false);
      contentManager.addContent(content);

      content.setPreferredFocusedComponent(new Computable<JComponent>() {
        public JComponent compute() {
          final AbstractProjectViewPane current = getCurrentProjectViewPane();
          return current != null ? current.getComponentToFocus() : null;
        }
      });
      toolWindow.setIcon(IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getToolWindowIconUrl()));
    }

    myCombo.addPopupMenuListener(new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {

      }

      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (!viewSelectionChanged()) {
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
        }
      }

      public void popupMenuCanceled(PopupMenuEvent e) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
      }
    });
    installLabelFocusListener();

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        splitterProportions.restoreSplitterProportions(myPanel);
      }
    });

    if (loadPaneExtensions) {
      ensurePanesLoaded();
    }

    isInitialized = true;
    doAddUninitializedPanes();
  }

  private void ensurePanesLoaded() {
    if (myExtensionsLoaded) return;
    myExtensionsLoaded = true;
    for(AbstractProjectViewPane pane: Extensions.getExtensions(AbstractProjectViewPane.EP_NAME, myProject)) {
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
      Disposer.register(this, pane);
    }
  }

  private final FocusListener myLabelFocusListener = new FocusListener() {
    public void focusGained(FocusEvent e) {
      if (!myCombo.isPopupVisible() && myCombo.isShowing()) {
        myCombo.requestFocusInWindow();
        myCombo.showPopup();
      }
    }

    public void focusLost(FocusEvent e) {

    }
  };

  private void installLabelFocusListener() {
    myLabel.addFocusListener(myLabelFocusListener);
  }

  private void removeLabelFocusListener() {
    myLabel.removeFocusListener(myLabelFocusListener);
  }

  private boolean viewSelectionChanged() {
    Pair<String, String> ids = (Pair<String, String>)myCombo.getSelectedItem();
    if (ids == null) return false;
    final String id = ids.first;
    String subId = ids.second;
    if (ids.equals(Pair.create(myCurrentViewId, myCurrentViewSubId))) return false;
    final AbstractProjectViewPane newPane = getProjectViewPaneById(id);
    if (newPane == null) return false;
    newPane.setSubId(subId);
    String[] subIds = newPane.getSubIds();

    if (subId == null && subIds.length != 0) {
      final String firstNonTrivialSubId = subIds[0];
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          changeView(id, firstNonTrivialSubId);
          newPane.setSubId(firstNonTrivialSubId);
        }
      });
    }
    else {
      showPane(newPane);
    }
    return true;
  }

  private void createToolbarActions() {
    myActionGroup.removeAll();
    if (ProjectViewDirectoryHelper.getInstance(myProject).supportsFlattenPackages()) {
      myActionGroup.addAction(new PaneOptionAction(myFlattenPackages, IdeBundle.message("action.flatten.packages"),
                                             IdeBundle.message("action.flatten.packages"), Icons.FLATTEN_PACKAGES_ICON,
                                             ourFlattenPackagesDefaults) {
        public void setSelected(AnActionEvent event, boolean flag) {
          final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
          final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

          super.setSelected(event, flag);

          selectionInfo.apply(viewPane);
        }
      }).setAsSecondary(true);
    }

    class FlattenPackagesDependableAction extends PaneOptionAction {
      FlattenPackagesDependableAction(Map<String, Boolean> optionsMap,
                                             final String text,
                                             final String description,
                                             final Icon icon,
                                             boolean optionDefaultValue) {
        super(optionsMap, text, description, icon, optionDefaultValue);
      }

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
                                                            IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png"),
                                                            ourAbbreviatePackagesDefaults) {
        public boolean isSelected(AnActionEvent event) {
          return super.isSelected(event) && isAbbreviatePackageNames(myCurrentViewId);
        }


        public void update(AnActionEvent e) {
          super.update(e);
          if (ScopeViewPane.ID.equals(myCurrentViewId)) {
            e.getPresentation().setEnabled(false);
          }
        }
      }).setAsSecondary(true);
    }
    myActionGroup.addAction(new PaneOptionAction(myShowMembers, IdeBundle.message("action.show.members"),
                                           IdeBundle.message("action.show.hide.members"),
                                           IconLoader.getIcon("/objectBrowser/showMembers.png"), ourShowMembersDefaults)).setAsSecondary(true);
    myActionGroup.addAction(myAutoScrollToSourceHandler.createToggleAction()).setAsSecondary(true);
    myActionGroup.addAction(myAutoScrollFromSourceHandler.createToggleAction()).setAsSecondary(true);
    myActionGroup.addAction(new SortByTypeAction()).setAsSecondary(true);

    myActionGroup.addAction(new ScrollFromSourceAction());
    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(new TreeExpander() {
      public void expandAll() {

      }

      public boolean canExpand() {
        return false;
      }

      public void collapseAll() {
        AbstractProjectViewPane pane = getCurrentProjectViewPane();
        JTree tree = pane.myTree;
        if (tree != null) {
          TreeUtil.collapseAll(tree, -1);
        }
      }

      public boolean canCollapse() {
        return true;
      }
    }, getComponent());
    myActionGroup.add(collapseAllAction);

    getCurrentProjectViewPane().addToolbarActions(myActionGroup);
  }

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

  public AbstractProjectViewPane getCurrentProjectViewPane() {
    return getProjectViewPaneById(myCurrentViewId);
  }

  public void refresh() {
    AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
    if (currentProjectViewPane != null) {
      // may be null for e.g. default project
      currentProjectViewPane.updateFromRoot(false);
    }
  }

  public void select(final Object element, VirtualFile file, boolean requestFocus) {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null) {
      viewPane.select(element, file, requestFocus);
    }
  }

  public ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus) {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null && viewPane instanceof AbstractProjectViewPSIPane) {
      return ((AbstractProjectViewPSIPane) viewPane).selectCB(element, file, requestFocus);
    } else {
      select(element, file, requestFocus);
      return new ActionCallback.Done();
    }
  }

  public void dispose() {
    myConnection.disconnect();
  }

  public JComponent getComponent() {
    return myDataProvider;
  }

  public String getCurrentViewId() {
    return myCurrentViewId;
  }

  private void updateToolWindowTitle() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager == null ? null : toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (toolWindow == null) return;
    String title = null;
    final AbstractProjectViewPane pane = getCurrentProjectViewPane();
    if (pane != null) {
      final DefaultMutableTreeNode selectedNode = pane.getSelectedNode();
      if (selectedNode != null) {
        final Object o = selectedNode.getUserObject();
        if (o instanceof ProjectViewNode) {
          title = ((ProjectViewNode)o).getTitle();
        }
      }
    }
    if (title == null) {
      final PsiElement element = (PsiElement)myDataProvider.getData(LangDataKeys.PSI_ELEMENT.getName());
      if (element != null) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          title = file.getVirtualFile().getPresentableUrl();
        }
        else if (element instanceof PsiDirectory) {
          title = PsiDirectoryFactory.getInstance(myProject).getQualifiedName((PsiDirectory) element, true);
        }
        else {
          title = element.toString();
        }
      }
      else {
        title = "";
        if (myProject != null) {
          title = myProject.getPresentableUrl();
        }
      }
    }

    toolWindow.setTitle(title);
  }

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

    PaneOptionAction(Map<String, Boolean> optionsMap,
                     final String text,
                     final String description,
                     final Icon icon,
                     boolean optionDefaultValue) {
      super(text, description, icon);
      myOptionsMap = optionsMap;
      myOptionDefaultValue = optionDefaultValue;
    }

    public boolean isSelected(AnActionEvent event) {
      return getPaneOptionValue(myOptionsMap, myCurrentViewId, myOptionDefaultValue);
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setPaneOption(myOptionsMap, flag, myCurrentViewId, true);
    }
  }

  public void changeView() {
    final List<AbstractProjectViewPane> views = new ArrayList<AbstractProjectViewPane>(myId2Pane.values());
    views.remove(getCurrentProjectViewPane());
    Collections.sort(views, PANE_WEIGHT_COMPARATOR);

    final JList list = new JBList(ArrayUtil.toObjectArray(views));
    list.setCellRenderer(new DefaultListCellRenderer() {
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
    Runnable runnable = new Runnable() {
      public void run() {
        if (list.getSelectedIndex() < 0) return;
        AbstractProjectViewPane pane = (AbstractProjectViewPane)list.getSelectedValue();
        changeView(pane.getId());
      }
    };

    new PopupChooserBuilder(list).
      setTitle(IdeBundle.message("title.popup.views")).
      setItemChoosenCallback(runnable).
      createPopup().showInCenterOf(getComponent());
  }

  public void changeView(@NotNull String viewId) {
    changeView(viewId, null);
  }

  public void changeView(@NotNull String viewId, @Nullable String subId) {
    AbstractProjectViewPane pane = getProjectViewPaneById(viewId);
    LOG.assertTrue(pane != null, "Project view pane not found: " + viewId + "; subId:" + subId);
    if (!viewId.equals(getCurrentViewId())
        || subId != null && !subId.equals(pane.getSubId()) ||
        // element not in model anymore
        ((DefaultComboBoxModel)myCombo.getModel()).getIndexOf(Pair.create(viewId, pane.getSubId())) == -1) {
      myCombo.setSelectedItem(Pair.create(viewId, subId));
      viewSelectionChanged();
    }
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      final PsiElement[] elements = getElementsToDelete();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    public void deleteElement(DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getElementsToDelete());
      List<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

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

    public Object getData(String dataId) {
      final AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
      if (currentProjectViewPane != null) {
        final Object paneSpecificData = currentProjectViewPane.getData(dataId);
        if (paneSpecificData != null) return paneSpecificData;
      }
      
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
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
      if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        PsiElement[] psiElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
        if (psiElements == null) return null;
        Set<VirtualFile> files = new LinkedHashSet<VirtualFile>();
        for (PsiElement element : psiElements) {
          if (element instanceof PsiFileSystemItem) {
            files.add(((PsiFileSystemItem)element).getVirtualFile());
          }
        }
        return files.size() > 0 ? VfsUtil.toVirtualFileArray(files) : null;
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
            public void deleteElement(DataContext dataContext) {
              detachLibrary(orderEntry, myProject);
            }

            public boolean canDeleteElement(DataContext dataContext) {
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
          return moduleByContentRoot(((PsiDirectory)selected).getVirtualFile());
        }
        else if (selected instanceof VirtualFile) {
          return moduleByContentRoot((VirtualFile)selected);
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

    private void detachLibrary(final LibraryOrderEntry orderEntry, final Project project) {
      final Module module = orderEntry.getOwnerModule();
      String message = IdeBundle.message("detach.library.from.module", orderEntry.getPresentableName(), module.getName());
      String title = IdeBundle.message("detach.library");
      int ret = Messages.showOkCancelDialog(project, message, title, Messages.getQuestionIcon());
      if (ret != 0) return;
      CommandProcessor.getInstance().executeCommand(module.getProject(), new Runnable() {
        public void run() {
          final Runnable action = new Runnable() {
            public void run() {
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
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
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
          Module module = moduleByContentRoot(((PsiDirectory)element).getVirtualFile());
          if (module != null) result.add(module);
        }
        else if (element instanceof VirtualFile) {
          Module module = moduleByContentRoot((VirtualFile)element);
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

  private Module moduleByContentRoot(VirtualFile file) {
    if (ProjectRootsUtil.isModuleContentRoot(file, myProject)) {
      Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
      if (module != null && !module.isDisposed()) {
        return module;
      }
    }

    return null;
  }

  private <T> List<T> getSelectedElements(Class<T> klass) {
    ArrayList<T> result = new ArrayList<T>();
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
    public void selectElement(PsiElement element) {
      selectPsiElement(element, false);
      boolean requestFocus = true;
      if (element != null) {
        final boolean isDirectory = element instanceof PsiDirectory;
        if (!isDirectory) {
          Editor editor = EditorHelper.openInEditor(element);
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

    public PsiDirectory[] getDirectories() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane != null) {
        return viewPane.getSelectedDirectories();
      }

      return PsiDirectory.EMPTY_ARRAY;
    }

    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }

  public void selectPsiElement(PsiElement element, boolean requestFocus) {
    if (element == null) return;
    VirtualFile virtualFile = PsiUtilBase.getVirtualFile(element);
    select(element, virtualFile, requestFocus);
  }


  private static void readOption(Element node, Map<String, Boolean> options) {
    if (node == null) return;
    List attributes = node.getAttributes();
    for (final Object attribute1 : attributes) {
      Attribute attribute = (Attribute)attribute1;
      options.put(attribute.getName(), Boolean.TRUE.toString().equals(attribute.getValue()) ? Boolean.TRUE : Boolean.FALSE);
    }
  }

  private static void writeOption(Element parentNode, Map<String, Boolean> optionsForPanes, String optionName) {
    Element e = new Element(optionName);
    for (Map.Entry<String, Boolean> entry : optionsForPanes.entrySet()) {
      final String key = entry.getKey();
      if (key != null) { //SCR48267
        e.setAttribute(key, Boolean.toString(entry.getValue().booleanValue()));
      }
    }

    parentNode.addContent(e);
  }

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

  private void readPaneState(Element panesElement) {
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

    splitterProportions.saveSplitterProportions(myPanel);
    try {
      splitterProportions.writeExternal(navigatorElement);
    }
    catch (WriteExternalException e) {
      // ignore
    }
    parentNode.addContent(navigatorElement);

    // for compatibility with idea 5.1
    @Deprecated @NonNls final String ATTRIBUTE_SPLITTER_PROPORTION = "splitterProportion";
    navigatorElement.setAttribute(ATTRIBUTE_SPLITTER_PROPORTION, "0.5");

    Element panesElement = new Element(ELEMENT_PANES);
    writePaneState(panesElement);
    parentNode.addContent(panesElement);
    return parentNode;
  }

  private void writePaneState(Element panesElement) {
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
      panesElement.addContent((Element) element.clone());
    }
  }

  public boolean isAutoscrollToSource(String paneId) {
    return getPaneOptionValue(myAutoscrollToSource, paneId, ourAutoscrollToSourceDefaults);
  }

  private void setAutoscrollToSource(boolean autoscrollMode, String paneId) {
    myAutoscrollToSource.put(paneId, autoscrollMode ? Boolean.TRUE : Boolean.FALSE);
  }

  public boolean isAutoscrollFromSource(String paneId) {
    return getPaneOptionValue(myAutoscrollFromSource, paneId, ourAutoscrollFromSourceDefaults);
  }

  private void setAutoscrollFromSource(boolean autoscrollMode, String paneId) {
    setPaneOption(myAutoscrollFromSource, autoscrollMode, paneId, false);
  }

  public boolean isFlattenPackages(String paneId) {
    return getPaneOptionValue(myFlattenPackages, paneId, ourFlattenPackagesDefaults);
  }

  public void setFlattenPackages(boolean flattenPackages, String paneId) {
    setPaneOption(myFlattenPackages, flattenPackages, paneId, true);
  }

  public boolean isShowMembers(String paneId) {
    return getPaneOptionValue(myShowMembers, paneId, ourShowMembersDefaults);
  }

  public boolean isHideEmptyMiddlePackages(String paneId) {
    return getPaneOptionValue(myHideEmptyPackages, paneId, ourHideEmptyPackagesDefaults);
  }

  public boolean isAbbreviatePackageNames(String paneId) {
    return getPaneOptionValue(myAbbreviatePackageNames, paneId, ourAbbreviatePackagesDefaults);
  }

  public boolean isShowLibraryContents(String paneId) {
    return getPaneOptionValue(myShowLibraryContents, paneId, ourShowLibraryContentsDefaults);
  }

  public void setShowLibraryContents(boolean showLibraryContents, String paneId) {
    setPaneOption(myShowLibraryContents, showLibraryContents, paneId, true);
  }

  public ActionCallback setShowLibraryContentsCB(boolean showLibraryContents, String paneId) {
    return setPaneOption(myShowLibraryContents, showLibraryContents, paneId, true);
  }

  public boolean isShowModules(String paneId) {
    return getPaneOptionValue(myShowModules, paneId, ourShowModulesDefaults);
  }

  public void setShowModules(boolean showModules, String paneId) {
    setPaneOption(myShowModules, showModules, paneId, true);
  }

  public void setHideEmptyPackages(boolean hideEmptyPackages, String paneId) {
    setPaneOption(myHideEmptyPackages, hideEmptyPackages, paneId, true);
  }

  public void setAbbreviatePackageNames(boolean abbreviatePackageNames, String paneId) {
    setPaneOption(myAbbreviatePackageNames, abbreviatePackageNames, paneId, true);
  }

  private ActionCallback setPaneOption(Map<String, Boolean> optionsMap, boolean value, String paneId, final boolean updatePane) {
    optionsMap.put(paneId, value ? Boolean.TRUE : Boolean.FALSE);
    if (updatePane) {
      final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
      if (pane != null) {
        return pane.updateFromRoot(false);
      }
    }
    return new ActionCallback.Done();
  }

  private static boolean getPaneOptionValue(Map<String, Boolean> optionsMap, String paneId, boolean defaultValue) {
    final Boolean value = optionsMap.get(paneId);
    return value == null ? defaultValue : value.booleanValue();
  }

  private class HideEmptyMiddlePackagesAction extends PaneOptionAction {
    private HideEmptyMiddlePackagesAction() {
      super(myHideEmptyPackages, "", "", null, ourHideEmptyPackagesDefaults);
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

      super.setSelected(event, flag);

      selectionInfo.apply(viewPane);
    }

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

    private SelectionInfo(Object[] elements) {
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
    private final Alarm myAlarm = new Alarm(myProject);

    private MyAutoScrollFromSourceHandler() {
      super(ProjectViewImpl.this.myProject, ProjectViewImpl.this);
    }

    public void install() {
      FileEditorManagerAdapter myEditorManagerListener = new FileEditorManagerAdapter() {
        public void selectionChanged(final FileEditorManagerEvent event) {
          final FileEditor newEditor = event.getNewEditor();
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            public void run() {
              if (myProject.isDisposed() || !myViewContentPanel.isShowing()) return;
              if (isAutoscrollFromSource(getCurrentViewId())) {
                if (newEditor instanceof TextEditor) {
                  Editor editor = ((TextEditor)newEditor).getEditor();
                  selectElementAtCaretNotLosingFocus(editor);
                } else if (newEditor != null) {
                  final VirtualFile file = FileEditorManagerEx.getInstanceEx(myProject).getFile(newEditor);
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
          }, 300, ModalityState.NON_MODAL);
        }
      };
      myFileEditorManager.addFileEditorManagerListener(myEditorManagerListener, this);
    }

    public void scrollFromSource() {
      final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
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

    private void selectElementAtCaretNotLosingFocus(final Editor editor) {
      if (IJSwingUtilities.hasFocus(getCurrentProjectViewPane().getComponentToFocus())) return;
      selectElementAtCaret(editor);
    }

    private void selectElementAtCaret(Editor editor) {
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;

      scrollFromFile(file, editor);
    }

    private void scrollFromFile(PsiFile file, @Nullable Editor editor) {
      final MySelectInContext selectInContext = new MySelectInContext(file, editor);

      final SelectInTarget target = mySelectInTargets.get(getCurrentViewId());
      if (target != null && target.canSelect(selectInContext)) {
        target.selectIn(selectInContext, false);
      }
    }

    public void dispose() {
    }

    protected boolean isAutoScrollMode() {
      return isAutoscrollFromSource(myCurrentViewId);
    }

    protected void setAutoScrollMode(boolean state) {
      setAutoscrollFromSource(state, myCurrentViewId);
      if (state) {
        final Editor editor = myFileEditorManager.getSelectedTextEditor();
        if (editor != null) {
          selectElementAtCaretNotLosingFocus(editor);
        }
      }
    }

    private class MySelectInContext implements SelectInContext {
      private final PsiFile myPsiFile;
      @Nullable private final Editor myEditor;

      private MySelectInContext(final PsiFile psiFile, @Nullable Editor editor) {
        myPsiFile = psiFile;
        myEditor = editor;
      }

      @NotNull
      public Project getProject() {
        return myProject;
      }

      private PsiFile getPsiFile() {
        return myPsiFile;
      }

      public FileEditorProvider getFileEditorProvider() {
        if (myPsiFile == null) return null;
        return new FileEditorProvider() {
          public FileEditor openFileEditor() {
            return myFileEditorManager.openFile(myPsiFile.getContainingFile().getVirtualFile(), false)[0];
          }
        };
      }

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

      @NotNull
      public VirtualFile getVirtualFile() {
        return getPsiFile().getVirtualFile();
      }

      public Object getSelectorInFile() {
        return getPsiElement();
      }
    }
  }

  public boolean isSortByType(String paneId) {
    return getPaneOptionValue(mySortByType, paneId, ourSortByTypeDefaults);
  }

  public void setSortByType(String paneId, final boolean sortByType) {
    setPaneOption(mySortByType, sortByType, paneId, false);
    final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
    pane.installComparator();
  }

  private class SortByTypeAction extends ToggleAction {
    private SortByTypeAction() {
      super(IdeBundle.message("action.sort.by.type"), IdeBundle.message("action.sort.by.type"),
            IconLoader.getIcon("/objectBrowser/sortByType.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isSortByType(getCurrentViewId());
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setSortByType(getCurrentViewId(), flag);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setVisible(getCurrentProjectViewPane() != null);
    }
  }

  private class ScrollFromSourceAction extends AnAction {
    private ScrollFromSourceAction() {
      super("Scroll from Source", "Select the file open in the active editor", IconLoader.getIcon("/general/autoscrollFromSource.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myAutoScrollFromSourceHandler.scrollFromSource();
    }
  }

  public Collection<String> getPaneIds() {
    return myId2Pane.keySet();
  }

  @Override
  public Collection<SelectInTarget> getSelectInTargets() {
    ensurePanesLoaded();
    return mySelectInTargets.values();
  }
}
