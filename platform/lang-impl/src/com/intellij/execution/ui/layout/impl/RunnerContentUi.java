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

package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.*;
import com.intellij.execution.ui.layout.actions.CloseViewAction;
import com.intellij.execution.ui.layout.actions.MinimizeViewAction;
import com.intellij.execution.ui.layout.actions.RestoreViewAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class RunnerContentUi implements ContentUI, Disposable, CellTransform.Facade, ViewContextEx, PropertyChangeListener, SwitchProvider,
                                        QuickActionProvider, DockContainer.Dialog {

  @NonNls public static final String LAYOUT = "Runner.Layout";
  @NonNls public static final String SETTINGS = "XDebugger.Settings";
  @NonNls public static final String VIEW_POPUP = "Runner.View.Popup";
  @NonNls public static final String VIEW_TOOLBAR = "Runner.View.Toolbar";

  ContentManager myManager;
  RunnerLayout myLayoutSettings;

  ActionManager myActionManager;
  String mySessionName;
  MyComponent myComponent = new MyComponent();

  private final Wrapper myToolbar = new Wrapper();
  final MyDragOutDelegate myDragOutDelegate = new MyDragOutDelegate();

  JBRunnerTabs myTabs;
  private final Comparator<TabInfo> myTabsComparator = new Comparator<TabInfo>() {
    public int compare(final TabInfo o1, final TabInfo o2) {
      //noinspection ConstantConditions
      return getTabFor(o1).getIndex() - getTabFor(o2).getIndex();
    }
  };
  Project myProject;

  ActionGroup myTopActions = new DefaultActionGroup();

  DefaultActionGroup myMinimizedViewActions = new DefaultActionGroup();

  Map<GridImpl, Wrapper> myMinimizedButtonsPlaceholder = new HashMap<GridImpl, Wrapper>();
  Map<GridImpl, Wrapper> myCommonActionsPlaceholder = new HashMap<GridImpl, Wrapper>();
  Map<GridImpl, AnAction[]> myContextActions = new HashMap<GridImpl, AnAction[]>();

  boolean myUiLastStateWasRestored;

  private final Set<Object> myRestoreStateRequestors = new HashSet<Object>();
  public static final DataKey<RunnerContentUi> KEY = DataKey.create("DebuggerContentUI");
  private String myActionsPlace = ActionPlaces.UNKNOWN;
  private final IdeFocusManager myFocusManager;

  private boolean myMinimizeActionEnabled = true;
  private boolean myMoveToGridActionEnabled = true;
  private final RunnerLayoutUi myRunnerUi;

  private final Map<String, LayoutAttractionPolicy> myAttractions = new HashMap<String, LayoutAttractionPolicy>();
  private final Map<String, LayoutAttractionPolicy> myConditionAttractions = new HashMap<String, LayoutAttractionPolicy>();

  private ActionGroup myAdditonalFocusActions;

  private final ActionCallback myInitialized = new ActionCallback();
  private boolean myToDisposeRemovedContent = true;

  private int myAttractionCount;
  private ActionGroup myLeftToolbarActions;

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;
  private RunnerContentUi myOriginal;
  private CopyOnWriteArraySet<Listener> myDockingListeners = new CopyOnWriteArraySet<Listener>();
  private Set<RunnerContentUi> myChildren = new TreeSet<RunnerContentUi>(new Comparator<RunnerContentUi>() {
    @Override
    public int compare(RunnerContentUi o1, RunnerContentUi o2) {
      return o1.myWindow - o2.myWindow;
    }
  }); 
  private int myWindow;
  private boolean myDisposing;

  public RunnerContentUi(Project project,
                         RunnerLayoutUi ui,
                         ActionManager actionManager,
                         IdeFocusManager focusManager,
                         RunnerLayout settings,
                         String sessionName) {
    myProject = project;
    myRunnerUi = ui;
    myLayoutSettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;
    myFocusManager = focusManager;
  }

  public RunnerContentUi(RunnerContentUi ui, RunnerContentUi original, int window) {
    this(ui.myProject, ui.myRunnerUi, ui.myActionManager, ui.myFocusManager, ui.myLayoutSettings, ui.mySessionName);
    myOriginal = original;
    original.myChildren.add(this);
    myWindow = window == 0 ? original.findFreeWindow() : window;
  }

  public void setTopActions(@NotNull final ActionGroup topActions, @NotNull String place) {
    myTopActions = topActions;
    myActionsPlace = place;

    rebuildCommonActions();
  }

  public void setAdditionalFocusActions(final ActionGroup group) {
    myAdditonalFocusActions = group;
    rebuildTabPopup();
  }

  public void setLeftToolbar(ActionGroup group, String place) {
    final ActionToolbar tb = myActionManager.createActionToolbar(place, group, false);
    tb.setTargetComponent(myComponent);
    myToolbar.setContent(tb.getComponent());
    myLeftToolbarActions = group;

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void initUi() {
    if (myTabs != null) return;

    myTabs = (JBRunnerTabs)new JBRunnerTabs(myProject, myActionManager, myFocusManager, this).setDataProvider(new DataProvider() {
      public Object getData(@NonNls final String dataId) {
        if (ViewContext.CONTENT_KEY.is(dataId)) {
          TabInfo info = myTabs.getTargetInfo();
          if (info != null) {
            return getGridFor(info).getData(dataId);
          }
        }
        else if (ViewContext.CONTEXT_KEY.is(dataId)) {
          return RunnerContentUi.this;
        }
        return null;
      }
    }).setTabLabelActionsAutoHide(false).setProvideSwitchTargets(false).setInnerInsets(new Insets(0, 0, 0, 0))
      .setToDrawBorderIfTabsHidden(false).setTabDraggingEnabled(isMoveToGridActionEnabled()).setUiDecorator(null).getJBTabs();
    rebuildTabPopup();

    myTabs.getPresentation().setPaintBorder(0, 0, 0, 0).setPaintFocus(false)
      .setRequestFocusOnLastFocusedComponent(true);
    myTabs.getComponent().setBackground(myToolbar.getBackground());
    myTabs.getComponent().setBorder(new EmptyBorder(0, 2, 0, 0));

    final NonOpaquePanel wrappper = new NonOpaquePanel(new BorderLayout(0, 0));
    wrappper.add(myToolbar, BorderLayout.WEST);
    wrappper.add(myTabs.getComponent(), BorderLayout.CENTER);

    myComponent.setContent(wrappper);

    myTabs.addListener(new TabsListener() {

      public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        if (oldSelection != null && !isStateBeingRestored()) {
          final GridImpl grid = getGridFor(oldSelection);
          if (grid != null) {
            grid.saveUiState();
          }
        }
      }

      @Override
      public void tabsMoved() {
        saveUiState();
      }

      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        if (!myTabs.getComponent().isShowing()) return;

        if (newSelection != null) {
          newSelection.stopAlerting();
          getGridFor(newSelection).processAddToUi(false);
        }

        if (oldSelection != null) {
          getGridFor(oldSelection).processRemoveFromUi();
        }
      }
    });
    myTabs.addTabMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          final TabInfo tabInfo = myTabs.findInfo(e);
          final GridImpl grid = tabInfo == null? null : getGridFor(tabInfo);
          final Content[] contents = grid != null ? CONTENT_KEY.getData(grid) : null;
          if (contents == null) return;
          // see GridCellImpl.closeOrMinimize as well
          if (CloseViewAction.isEnabled(contents)) {
            CloseViewAction.perform(RunnerContentUi.this, contents[0]);
          }
          else if (MinimizeViewAction.isEnabled(RunnerContentUi.this, contents, ViewContext.TAB_TOOLBAR_PLACE)) {
            grid.getCellFor(contents[0]).minimize(contents[0]);
          }
        }
      }
    });

    if (myOriginal != null) {
      final ContentManager manager = ContentFactory.SERVICE.getInstance().createContentManager(this, false, myProject);
      Disposer.register((Disposable)myRunnerUi, manager);
      manager.getComponent();
    } else {
      DockManager.getInstance(myProject).register(this);
    }
  }

  private void rebuildTabPopup() {
    myTabs.setPopupGroup(getCellPopupGroup(TAB_POPUP_PLACE), TAB_POPUP_PLACE, true);

    final ArrayList<GridImpl> grids = getGrids();
    for (GridImpl each : grids) {
      each.rebuildTabPopup();
    }
  }

  public ActionGroup getCellPopupGroup(final String place) {
    final ActionGroup original = (ActionGroup)myActionManager.getAction(VIEW_POPUP);
    final ActionGroup focusPlaceholder = (ActionGroup)myActionManager.getAction("Runner.Focus");

    DefaultActionGroup group = new DefaultActionGroup(VIEW_POPUP, original.isPopup());

    final AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(), place, new Presentation(),
                                                  ActionManager.getInstance(), 0);
    final AnAction[] originalActions = original.getChildren(event);


    for (final AnAction each : originalActions) {
      if (each == focusPlaceholder) {
        final AnAction[] focusActions = ((ActionGroup)each).getChildren(event);
        for (AnAction eachFocus : focusActions) {
          group.add(eachFocus);
        }
        if (myAdditonalFocusActions != null) {
          final AnAction[] addins = myAdditonalFocusActions.getChildren(event);
          for (AnAction eachAddin : addins) {
            group.add(eachAddin);
          }
        }
      }
      else {
        group.add(each);
      }
    }
    return group;
  }

  @Override
  public boolean isOriginal() {
    return myOriginal == null;
  }

  @Override
  public int getWindow() {
    return myWindow;
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    Content content = (Content)evt.getSource();
    final GridImpl grid = getGridFor(content, false);
    if (grid == null) return;

    final GridCellImpl cell = grid.findCell(content);
    if (cell == null) return;

    final String property = evt.getPropertyName();
    if (Content.PROP_ALERT.equals(property)) {
      attract(content, true);
    }
    else if (Content.PROP_DISPLAY_NAME.equals(property)
             || Content.PROP_ICON.equals(property)
             || Content.PROP_ACTIONS.equals(property)
             || Content.PROP_DESCRIPTION.equals(property)) {
      cell.updateTabPresentation(content);
      updateTabsUI(false);
    }
  }


  public void processBounce(Content content, final boolean activate) {
    final GridImpl grid = getGridFor(content, false);
    if (grid == null) return;

    final GridCellImpl cell = grid.findCell(content);
    if (cell == null) return;


    final TabInfo tab = myTabs.findInfo(grid);
    if (tab == null) return;


    if (getSelectedGrid() != grid) {
      tab.setAlertIcon(content.getAlertIcon());
      if (activate) {
        tab.fireAlert();
      }
      else {
        tab.stopAlerting();
      }
    }
    else {
      grid.processAlert(content, activate);
    }
  }

  @Override
  public ActionCallback detachTo(int window, GridCell cell) {
    if (myOriginal != null) {
      return myOriginal.detachTo(window, cell);
    }
    RunnerContentUi target = null;
    if (window > 0) {
      for (RunnerContentUi child : myChildren) {
        if (child.myWindow == window) {
          target = child;
          break;
        }
      }
    }
    final GridCellImpl gridCell = (GridCellImpl)cell;
    final Content[] contents = gridCell.getContents();
    storeDefaultIndices(contents);
    for (Content content : contents) {
      content.putUserData(RunnerLayout.DROP_INDEX, getStateFor(content).getTab().getIndex());
    }
    Dimension size = gridCell.getSize();
    if (size == null) {
      size = new Dimension(200, 200);
    }
    final DockableGrid content = new DockableGrid(null, null, size, Arrays.asList(contents), window);
    if (target != null) {
      target.add(content, null);
    } else {
      Point location = gridCell.getLocation();
      if (location == null) {
        location = getComponent().getLocationOnScreen();
      }
      location.translate(size.width / 2, size.height / 2);
      getDockManager().createNewDockContainerFor(content, new RelativePoint(location));
    }
    return new ActionCallback.Done();
  }

  private void storeDefaultIndices(Content[] contents) {
    int i = 0;
    for (Content content : contents) {
      content.putUserData(RunnerLayout.DEFAULT_INDEX, getStateFor(content).getTab().getDefaultIndex());
      //content.putUserData(CONTENT_NUMBER, i++);
    }
  }

  @Override
  public RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(myTabs.getComponent());
  }

  @Override
  public boolean canAccept(DockableContent content, RelativePoint point) {
    if (!(content instanceof DockableGrid)) {
      return false;
    }
    final RunnerContentUi ui = ((DockableGrid)content).getOriginalRunnerUi();
    return ui.getProject() == myProject && ui.mySessionName.equals(mySessionName);
  }

  @Override
  public JComponent getComponent() {
    initUi();
    return myComponent;
  }

  @Override
  public JComponent getContainerComponent() {
    initUi();
    return myManager.getComponent();
  }

  @Override
  public void add(DockableContent dockable, RelativePoint dropTarget) {
    final DockableGrid dockableGrid = (DockableGrid)dockable;
    final RunnerContentUi prev = dockableGrid.getRunnerUi();

    saveUiState();

    final List<Content> contents = dockableGrid.getContents();
    final boolean wasRestoring = myOriginal != null && myOriginal.isStateBeingRestored();
    setStateIsBeingRestored(true, this);
    try {
      final Point point = dropTarget != null ? dropTarget.getPoint(myComponent) : null;
      boolean hadGrid = !myTabs.shouldAddToGlobal(point);

      for (Content content : contents) {
        final View view = getStateFor(content);
        if (view.isMinimizedInGrid()) continue;
        prev.myManager.removeContent(content, false);
        myManager.removeContent(content, false);
        if (hadGrid && !wasRestoring) {
          view.assignTab(getTabFor(getSelectedGrid()));
          view.setPlaceInGrid(myLayoutSettings.getDefaultGridPlace(content));
        } else if (contents.size() == 1 && !wasRestoring) {
          view.assignTab(null);
          view.setPlaceInGrid(myLayoutSettings.getDefaultGridPlace(content));
        }
        view.setWindow(myWindow);
        myManager.addContent(content);
      }
    } finally {
      setStateIsBeingRestored(false, this);
    }

    saveUiState();

    updateTabsUI(true);
  }

  @Override
  public void closeAll() {
    final Content[] contents = myManager.getContents();
    for (Content content : contents) {
      getStateFor(content).setWindow(0);
    }
    myManager.removeAllContents(false);
    for (Content content : contents) {
      myOriginal.myManager.addContent(content);
      myOriginal.findCellFor(content).minimize(content);
    }
  }

  @Override
  public void addListener(final Listener listener, Disposable parent) {
    myDockingListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myDockingListeners.remove(listener);
      }
    });
  }

  @Override
  public boolean isEmpty() {
    return myTabs.isEmptyVisible() || myDisposing;
  }

  @Override
  public Image startDropOver(DockableContent content, RelativePoint point) {
    return null;
  }

  @Override
  public Image processDropOver(DockableContent content, RelativePoint point) {
    JBTabs current = getTabsAt(content, point);

    if (myCurrentOver != null && myCurrentOver != current) {
      resetDropOver(content);
    }

    if (myCurrentOver == null && current != null) {
      myCurrentOver = current;
      Presentation presentation = content.getPresentation();
      myCurrentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      myCurrentOverImg = myCurrentOver.startDropOver(myCurrentOverInfo, point);
    }

    if (myCurrentOver != null) {
      myCurrentOver.processDropOver(myCurrentOverInfo, point);
    }

    return myCurrentOverImg;
  }

  @Nullable
  private JBTabs getTabsAt(DockableContent content, RelativePoint point) {
    if (content instanceof DockableGrid) {
      final Point p = point.getPoint(getComponent());
      Component c = SwingUtilities.getDeepestComponentAt(getComponent(), p.x, p.y);
      while (c != null) {
        if (c instanceof JBRunnerTabs) {
          return (JBTabs)c;
        }
        c = c.getParent();
      }
    }
    return null;
  }

  @Override
  public void resetDropOver(DockableContent content) {
    if (myCurrentOver != null) {
      myCurrentOver.resetDropOver(myCurrentOverInfo);
      myCurrentOver = null;
      myCurrentOverInfo = null;
      myCurrentOverImg = null;
    }
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return myOriginal != null;
  }

  public boolean isCycleRoot() {
    return false;
  }

  public void setManager(final ContentManager manager) {
    assert myManager == null;

    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        final GridImpl grid = getGridFor(event.getContent(), true);

        if (grid == null) {
          return;
        }

        grid.add(event.getContent());

        if (getSelectedGrid() == grid) {
          grid.processAddToUi(false);
        }

        if (myManager.getComponent().isShowing() && !isStateBeingRestored()) {
          grid.restoreLastUiState();
        }

        updateTabsUI(false);

        event.getContent().addPropertyChangeListener(RunnerContentUi.this);
        fireContentOpened(event.getContent());
      }

      public void contentRemoved(final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(RunnerContentUi.this);

        GridImpl grid = (GridImpl)findGridFor(event.getContent());
        if (grid != null) {
          grid.remove(event.getContent());
          grid.processRemoveFromUi();
          removeGridIfNeeded(grid);
        }
        updateTabsUI(false);
        fireContentClosed(event.getContent());
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        if (isStateBeingRestored()) return;

        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          select(event.getContent(), false);
        }
      }
    });
  }

  @Nullable
  private GridImpl getSelectedGrid() {
    TabInfo selection = myTabs.getSelectedInfo();
    return selection != null ? getGridFor(selection) : null;
  }

  private void removeGridIfNeeded(GridImpl grid) {
    if (grid.isEmpty()) {
      myTabs.removeTab(myTabs.findInfo(grid));
      myMinimizedButtonsPlaceholder.remove(grid);
      myCommonActionsPlaceholder.remove(grid);
      Disposer.dispose(grid);
    }
  }

  @Nullable
  private GridImpl getGridFor(Content content, boolean createIfMissing) {
    GridImpl grid = (GridImpl)findGridFor(content);
    if (grid != null || !createIfMissing) return grid;

    grid = new GridImpl(this, mySessionName);

    if (myCurrentOver != null || myOriginal != null) {
      Integer forcedDropIndex = content.getUserData(RunnerLayout.DROP_INDEX);
      final int index = myTabs.getDropInfoIndex() + (myOriginal != null ? myOriginal.getTabOffsetFor(this) : 0);
      final int dropIndex = forcedDropIndex != null ? forcedDropIndex : index;
      if (forcedDropIndex == null) {
        moveFollowingTabs(dropIndex);
      }
      final int defaultIndex = content.getUserData(RunnerLayout.DEFAULT_INDEX);
      final TabImpl tab = myLayoutSettings.getOrCreateTab(forcedDropIndex != null ? forcedDropIndex : -1);
      tab.setDefaultIndex(defaultIndex);
      tab.setIndex(dropIndex);
      getStateFor(content).assignTab(tab);
      content.putUserData(RunnerLayout.DROP_INDEX, null);
      content.putUserData(RunnerLayout.DEFAULT_INDEX, null);
    }
    
    TabInfo tab = new TabInfo(grid).setObject(getStateFor(content).getTab()).setText("Tab");


    Wrapper left = new Wrapper();
    myCommonActionsPlaceholder.put(grid, left);


    Wrapper minimizedToolbar = new Wrapper();
    myMinimizedButtonsPlaceholder.put(grid, minimizedToolbar);


    final Wrapper searchComponent = new Wrapper();
    if (content.getSearchComponent() != null) {
      searchComponent.setContent(content.getSearchComponent());
    }

    TwoSideComponent right = new TwoSideComponent(searchComponent, minimizedToolbar);


    NonOpaquePanel sideComponent = new TwoSideComponent(left, right);

    tab.setSideComponent(sideComponent);

    tab.setTabLabelActions((ActionGroup)myActionManager.getAction(VIEW_TOOLBAR), TAB_TOOLBAR_PLACE);

    myTabs.addTab(tab);
    myTabs.sortTabs(myTabsComparator);

    return grid;
  }

  private void moveFollowingTabs(int index) {
    if (myOriginal != null) {
      myOriginal.moveFollowingTabs(index);
      return;
    }
    moveFollowingTabs(index, myTabs);
    for (RunnerContentUi child : myChildren) {
      moveFollowingTabs(index, child.myTabs);
    }
  }

  public ActionGroup getSettingsActions() {
    return (ActionGroup)myActionManager.getAction(SETTINGS);
  }

  public ContentManager getContentManager(Content content) {
    if (hasContent(myManager, content)) {
      return myManager;
    }
    for (RunnerContentUi child : myChildren) {
      if (hasContent(child.myManager, content)) {
        return child.myManager;
      }
    }
    return myManager;
  }

  private static boolean hasContent(ContentManager manager, Content content) {
    for (Content c : manager.getContents()) {
      if (c == content) {
        return true;
      }
    }
    return false;
  }

  private static void moveFollowingTabs(int index, final JBRunnerTabs tabs) {
    for (TabInfo info : tabs.getTabs()) {
      final TabImpl tab = getTabFor(info);
      final int tabIndex = tab != null ? tab.getIndex() : -1;
      if (tabIndex >= index) {
        tab.setIndex(tabIndex + 1);
      }
    }
  }

  private int getTabOffsetFor(RunnerContentUi ui) {
    int offset = myTabs.getTabCount();
    for (RunnerContentUi child : myChildren) {
      if (child == ui) break;
      offset += child.myTabs.getTabCount();
    }
    return offset;
  }

  @Nullable
  public GridCell findCellFor(final Content content) {
    GridImpl cell = getGridFor(content, false);
    return cell != null ? cell.getCellFor(content) : null;
  }

  private boolean rebuildToolbar() {
    boolean hasToolbarContent = rebuildCommonActions();
    hasToolbarContent |= rebuildMinimizedActions();
    return hasToolbarContent;
  }

  private boolean rebuildCommonActions() {
    boolean hasToolbarContent = false;
    for (Map.Entry<GridImpl, Wrapper> entry : myCommonActionsPlaceholder.entrySet()) {
      Wrapper eachPlaceholder = entry.getValue();
      List<Content> contentList = entry.getKey().getContents();

      Set<Content> contents = new HashSet<Content>();
      contents.addAll(contentList);

      DefaultActionGroup groupToBuild;
      JComponent contextComponent = null;
      if (isHorizontalToolbar() && contents.size() == 1) {
        Content content = contentList.get(0);
        groupToBuild = new DefaultActionGroup();
        if (content.getActions() != null) {
          groupToBuild.addAll(content.getActions());
          groupToBuild.addSeparator();
          contextComponent = content.getActionsContextComponent();
        }
        groupToBuild.addAll(myTopActions);
      }
      else {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(myTopActions);
        groupToBuild = group;
      }

      final AnAction[] actions = groupToBuild.getChildren(null);
      if (!Arrays.equals(actions, myContextActions.get(entry.getKey()))) {
        ActionToolbar tb = myActionManager.createActionToolbar(myActionsPlace, groupToBuild, true);
        tb.getComponent().setBorder(null);
        tb.setTargetComponent(contextComponent);
        eachPlaceholder.setContent(tb.getComponent());
      }

      if (groupToBuild.getChildrenCount() > 0) {
        hasToolbarContent = true;
      }

      myContextActions.put(entry.getKey(), actions);
    }

    return hasToolbarContent;
  }

  private boolean rebuildMinimizedActions() {
    for (Map.Entry<GridImpl, Wrapper> entry : myMinimizedButtonsPlaceholder.entrySet()) {
      Wrapper eachPlaceholder = entry.getValue();
      ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, myMinimizedViewActions, true);
      tb.getComponent().setBorder(null);
      tb.setReservePlaceAutoPopupIcon(false);
      JComponent minimized = tb.getComponent();
      eachPlaceholder.setContent(minimized);
    }

    myTabs.getComponent().revalidate();
    myTabs.getComponent().repaint();

    return myMinimizedViewActions.getChildrenCount() > 0;
  }

  private void updateTabsUI(final boolean validateNow) {
    boolean hasToolbarContent = rebuildToolbar();

    Set<String> usedNames = new HashSet<String>();
    List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      hasToolbarContent |= updateTabUI(each, usedNames);
    }
    int tabsCount = tabs.size();
    for (RunnerContentUi child : myChildren) {
      tabsCount += child.myTabs.getTabCount();
    }
    myTabs.getPresentation().setHideTabs(!hasToolbarContent && tabsCount <= 1 && myOriginal == null);
    myTabs.updateTabActions(validateNow);

    if (validateNow) {
      myTabs.sortTabs(myTabsComparator);
    }
  }

  private boolean updateTabUI(TabInfo tab, Set<String> usedNames) {
    TabImpl t = getTabFor(tab);
    if (t == null) {
      return false;
    }

    Icon icon = t.getIcon();

    GridImpl grid = getGridFor(tab);
    boolean hasToolbarContent = grid.updateGridUI();

    List<Content> contents = grid.getContents();
    String title = contents.size() > 1 ? t.getDisplayName() : null;
    if (title == null) {
      final String name = myLayoutSettings.getDefaultDisplayName(t.getDefaultIndex());
      if (name != null && contents.size() > 1 && !usedNames.contains(name)) {
        title = name;
      } else {
        title = StringUtil.join(contents, new NotNullFunction<Content, String>() {
          @NotNull
          @Override
          public String fun(Content dom) {
            return dom.getTabName();
          }
        },  " | ");
      }
    }
    usedNames.add(title);

    boolean hidden = true;
    for (Content content : contents) {
      if (!grid.isMinimized(content)) {
        hidden = false;
        break;
      }
    }
    tab.setHidden(hidden);
    if (icon == null && contents.size() == 1) {
      icon = contents.get(0).getIcon();
    }

    tab.setDragOutDelegate(myTabs.getTabs().size() > 1 || !isOriginal() ? myDragOutDelegate : null);

    Tab gridTab = grid.getTab();
    tab.setText(title).setIcon(gridTab != null && gridTab.isDefault() && contents.size() > 1 ? null : icon);

    return hasToolbarContent;
  }

  private ActionCallback restoreLastUiState() {
    if (isStateBeingRestored()) return new ActionCallback.Rejected();

    try {
      setStateIsBeingRestored(true, this);

      List<TabInfo> tabs = new ArrayList<TabInfo>();
      tabs.addAll(myTabs.getTabs());

      final ActionCallback result = new ActionCallback(tabs.size());

      for (TabInfo each : tabs) {
        getGridFor(each).restoreLastUiState().notifyWhenDone(result);
      }

      return result;
    }
    finally {
      setStateIsBeingRestored(false, this);
    }
  }

  public void saveUiState() {
    if (isStateBeingRestored()) return;

    if (myOriginal != null) {
      myOriginal.saveUiState();
      return;
    }
    int offset = updateTabsIndices(myTabs, 0);
    for (RunnerContentUi child : myChildren) {
      offset = updateTabsIndices(child.myTabs, offset);
    }

    doSaveUiState();
  }

  private static int updateTabsIndices(final JBRunnerTabs tabs, int offset) {
    for (TabInfo each : tabs.getTabs()) {
      final int index = tabs.getIndexOf(each);
      final TabImpl tab = getTabFor(each);
      if (tab != null) tab.setIndex(index >= 0 ? index + offset : index);
    }
    return offset + tabs.getTabCount();
  }

  private void doSaveUiState() {
    if (isStateBeingRestored()) return;

    for (TabInfo each : myTabs.getTabs()) {
      GridImpl eachGrid = getGridFor(each);
      eachGrid.saveUiState();
    }

    for (RunnerContentUi child : myChildren) {
      child.doSaveUiState();
    }
  }

  @Nullable
  public Tab getTabFor(final Grid grid) {
    TabInfo info = myTabs.findInfo((Component)grid);
    return getTabFor(info);
  }

  @Override
  public void showNotify() {
    final Window window = SwingUtilities.getWindowAncestor(myComponent);
    if (window instanceof IdeFrame.Child) {
      ((IdeFrame.Child)window).setFrameTitle(mySessionName);
    }
  }

  @Override
  public void hideNotify() {}

  @Nullable
  private static TabImpl getTabFor(@Nullable final TabInfo tab) {
    if (tab == null) {
      return null;
    }
    return (TabImpl)tab.getObject();
  }

  private static GridImpl getGridFor(TabInfo tab) {
    return (GridImpl)tab.getComponent();
  }

  @Nullable
  public Grid findGridFor(Content content) {
    TabImpl tab = (TabImpl)getStateFor(content).getTab();
    for (TabInfo each : myTabs.getTabs()) {
      TabImpl t = getTabFor(each);
      if (t != null && t.equals(tab)) return getGridFor(each);
    }

    return null;
  }

  private ArrayList<GridImpl> getGrids() {
    ArrayList<GridImpl> result = new ArrayList<GridImpl>();
    for (TabInfo each : myTabs.getTabs()) {
      result.add(getGridFor(each));
    }
    return result;
  }


  public void setHorizontalToolbar(final boolean state) {
    myLayoutSettings.setToolbarHorizontal(state);
    for (GridImpl each : getGrids()) {
      each.setToolbarHorizontal(state);
    }

    myContextActions.clear();
    updateTabsUI(false);
  }


  public boolean isSingleSelection() {
    return false;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public boolean canBeEmptySelection() {
    return true;
  }

  public void beforeDispose() {
    if (myOriginal != null) {
      myDisposing = true;
      fireContentClosed(null);
    }
  }

  public boolean canChangeSelectionTo(Content content, boolean implicit) {
    if (implicit) {
      GridImpl grid = getGridFor(content, false);
      if (grid != null) {
        return !grid.isMinimized(content);
      }
    }

    return true;
  }

  @Override
  public String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @Override
  public String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  @Override
  public String getPreviousContentActionName() {
    return "Select Previous Tab";
  }

  @Override
  public String getNextContentActionName() {
    return "Select Next Tab";
  }

  public void dispose() {
    if (myOriginal != null) {
      myOriginal.myChildren.remove(this);
    }
    myMinimizedButtonsPlaceholder.clear();
    myCommonActionsPlaceholder.clear();
    myContextActions.clear();
  }

  public void restoreLayout() {
    final RunnerContentUi[] children = myChildren.toArray(new RunnerContentUi[myChildren.size()]);
    final List<Content> contents = new ArrayList<Content>();
    Collections.addAll(contents, myManager.getContents());
    for (RunnerContentUi child : children) {
      Collections.addAll(contents, child.myManager.getContents());
    }
    for (AnAction action : myMinimizedViewActions.getChildren(null)) {
      final Content content = ((RestoreViewAction)action).getContent();
      contents.add(content);
    }
    Content[] all = contents.toArray(new Content[contents.size()]);
    Arrays.sort(all, new Comparator<Content>() {
      @Override
      public int compare(Content content, Content content1) {
        final int i = getStateFor(content).getTab().getDefaultIndex();
        final int i1 = getStateFor(content1).getTab().getDefaultIndex();
        return i - i1;
      }
    });
    
    setStateIsBeingRestored(true, this);
    try {
      for (RunnerContentUi child : children) {
        child.myManager.removeAllContents(false);
      }
      myManager.removeAllContents(false);
      myMinimizedViewActions.removeAll();
    }
    finally {
      setStateIsBeingRestored(false, this);
    }

    myLayoutSettings.resetToDefault();
    for (Content each : all) {
      myManager.addContent(each);
    }

    updateTabsUI(true);
  }

  public boolean isStateBeingRestored() {
    return !myRestoreStateRequestors.isEmpty();
  }

  public void setStateIsBeingRestored(final boolean restoredNow, final Object requestor) {
    if (restoredNow) {
      myRestoreStateRequestors.add(requestor);
    }
    else {
      myRestoreStateRequestors.remove(requestor);
    }
  }

  public ActionGroup getLayoutActions() {
    return (ActionGroup)myActionManager.getAction(LAYOUT);
  }

  public void updateActionsImmediately() {
    if (myToolbar.getTargetComponent() instanceof ActionToolbar) {
      ((ActionToolbar)myToolbar.getTargetComponent()).updateActionsImmediately();
    }
  }

  public void setMinimizeActionEnabled(final boolean enabled) {
    myMinimizeActionEnabled = enabled;
  }

  public void setMovetoGridActionEnabled(final boolean enabled) {
    myMoveToGridActionEnabled = enabled;
  }

  public boolean isMinimizeActionEnabled() {
    return myMinimizeActionEnabled && myOriginal == null;
  }

  public boolean isMoveToGridActionEnabled() {
    return myMoveToGridActionEnabled;
  }

  public void setPolicy(String contentId, final LayoutAttractionPolicy policy) {
    myAttractions.put(contentId, policy);
  }

  public void setConditionPolicy(final String condition, final LayoutAttractionPolicy policy) {
    myConditionAttractions.put(condition, policy);
  }

  private static LayoutAttractionPolicy getOrCreatePolicyFor(String key,
                                                             Map<String, LayoutAttractionPolicy> map,
                                                             LayoutAttractionPolicy defaultPolicy) {
    LayoutAttractionPolicy policy = map.get(key);
    if (policy == null) {
      policy = defaultPolicy;
      map.put(key, policy);
    }
    return policy;
  }

  @Nullable
  public Content findContent(final String key) {
    final ContentManager manager = getContentManager();
    if (manager == null || key == null) return null;

    Content[] contents = manager.getContents();
    for (Content content : contents) {
      String kind = content.getUserData(ViewImpl.ID);
      if (key.equals(kind)) {
        return content;
      }
    }

    return null;
  }

  public void setToDisposeRemovedContent(final boolean toDispose) {
    myToDisposeRemovedContent = toDispose;
  }

  public boolean isToDisposeRemovedContent() {
    return myToDisposeRemovedContent;
  }

  private class MyComponent extends Wrapper.FocusHolder implements DataProvider, QuickActionProvider {

    private boolean myWasEverAdded;

    public MyComponent() {
      setOpaque(true);
      setFocusCycleRoot(true);
      setBorder(new ToolWindow.Border(true, false, true, true));
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (KEY.is(dataId)) {
        return RunnerContentUi.this;
      }
      else {
        return null;
      }
    }

    @Override
    public String getName() {
      return RunnerContentUi.this.getName();
    }

    public List<AnAction> getActions(boolean originalProvider) {
      return RunnerContentUi.this.getActions(originalProvider);
    }

    public JComponent getComponent() {
      return RunnerContentUi.this.getComponent();
    }

    public boolean isCycleRoot() {
      return RunnerContentUi.this.isCycleRoot();
    }

    public void addNotify() {
      super.addNotify();

      if (!myUiLastStateWasRestored && myOriginal == null) {
        myUiLastStateWasRestored = true;

        // [kirillk] this is done later since restoreUiState doesn't work properly in the addNotify call chain
        //todo to investigate and to fix (may cause extra flickering)
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            restoreLastUiState().doWhenDone(new Runnable() {
              public void run() {
                if (!myWasEverAdded) {
                  myWasEverAdded = true;
                  attractOnStartup();
                  myInitialized.setDone();
                }
              }
            });
          }
        });
      }
    }

    public void removeNotify() {
      super.removeNotify();

      if (Disposer.isDisposed(RunnerContentUi.this)) return;

      saveUiState();
    }
  }

  @SuppressWarnings({"SSBasedInspection"})
  // [kirillk] this is done later since "startup" attractions should be done gently, only if no explicit calls are done
  private void attractOnStartup() {
    final int currentCount = myAttractionCount;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (currentCount < myAttractionCount) return;
        attractByCondition(LayoutViewOptions.STARTUP, false);
      }
    });
  }

  public void attract(final Content content, boolean afterInitialized) {
    processAttraction(content.getUserData(ViewImpl.ID), myAttractions, new LayoutAttractionPolicy.Bounce(), afterInitialized, true);
  }

  public void attractByCondition(String condition, boolean afterInitialized) {
    processAttraction(myLayoutSettings.getToFocus(condition), myConditionAttractions, myLayoutSettings.getAttractionPolicy(condition),
                      afterInitialized, true);
  }

  public void clearAttractionByCondition(String condition, boolean afterInitialized) {
    processAttraction(myLayoutSettings.getToFocus(condition), myConditionAttractions, new LayoutAttractionPolicy.FocusOnce(),
                      afterInitialized, false);
  }

  private void processAttraction(final String contentId,
                                 final Map<String, LayoutAttractionPolicy> policyMap,
                                 final LayoutAttractionPolicy defaultPolicy,
                                 final boolean afterInitialized,
                                 final boolean activate) {
    IdeFocusManager.getInstance(getProject()).doWhenFocusSettlesDown(new Runnable() {
      public void run() {
        myInitialized.processOnDone(new Runnable() {
          public void run() {
            Content content = findContent(contentId);
            if (content == null) return;

            final LayoutAttractionPolicy policy = getOrCreatePolicyFor(contentId, policyMap, defaultPolicy);
            if (activate) {
              myAttractionCount++;
              policy.attract(content, myRunnerUi);
            }
            else {
              policy.clearAttraction(content, myRunnerUi);
            }
          }
        }, afterInitialized);
      }
    });
  }


  public static boolean ensureValid(JComponent c) {
    if (c.getRootPane() == null) return false;

    Container eachParent = c.getParent();
    while (eachParent != null && eachParent.isValid()) {
      eachParent = eachParent.getParent();
    }

    if (eachParent == null) {
      eachParent = c.getRootPane();
    }

    eachParent.validate();

    return true;
  }

  public ContentUI getContentUI() {
    return this;
  }

  public void minimize(final Content content, final CellTransform.Restore restore) {
    final Ref<AnAction> restoreAction = new Ref<AnAction>();
    myManager.removeContent(content, false);
    restoreAction.set(new RestoreViewAction(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        myMinimizedViewActions.remove(restoreAction.get());
        final GridImpl grid = getGridFor(content, false);
        if (grid == null) {
          getStateFor(content).assignTab(myLayoutSettings.getOrCreateTab(-1));
        } else {
          //noinspection ConstantConditions
          ((GridCellImpl)findCellFor(content)).restore(content);
        }
        getStateFor(content).setMinimizedInGrid(false);
        myManager.addContent(content);
        saveUiState();
        select(content, true);
        updateTabsUI(false);
        return new ActionCallback.Done();
      }
    }));

    myMinimizedViewActions.add(restoreAction.get());

    saveUiState();
    updateTabsUI(false);
  }

  public Project getProject() {
    return myProject;
  }

  public CellTransform.Facade getCellTransform() {
    return this;
  }

  public ContentManager getContentManager() {
    return myManager;
  }

  public ActionManager getActionManager() {
    return myActionManager;
  }

  public RunnerLayout getLayoutSettings() {
    return myLayoutSettings;
  }

  public View getStateFor(final Content content) {
    return myLayoutSettings.getStateFor(content);
  }

  public boolean isHorizontalToolbar() {
    return myLayoutSettings.isToolbarHorizontal();
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    final GridImpl grid = (GridImpl)findGridFor(content);
    if (grid == null) return new ActionCallback.Done();


    final TabInfo info = myTabs.findInfo(grid);
    if (info == null) return new ActionCallback.Done();


    final ActionCallback result = new ActionCallback();
    myTabs.select(info, false).doWhenDone(new Runnable() {
      public void run() {
        grid.select(content, requestFocus).notifyWhenDone(result);
      }
    });


    return result;
  }

  public void validate(final Content content, final ActiveRunnable toRestore) {
    final TabInfo current = myTabs.getSelectedInfo();
    myTabs.getPresentation().setPaintBlocked(true, true);

    select(content, false).doWhenDone(new Runnable() {
      public void run() {
        myTabs.getComponent().validate();
        toRestore.run().doWhenDone(new Runnable() {
          public void run() {
            myTabs.select(current, true);
            myTabs.getPresentation().setPaintBlocked(false, true);
          }
        });
      }
    });
  }

  private static class TwoSideComponent extends NonOpaquePanel {
    private TwoSideComponent(JComponent left, JComponent right) {
      setLayout(new CommonToolbarLayout(left, right));
      add(left);
      add(right);
    }
  }

  private static class CommonToolbarLayout extends AbstractLayoutManager {
    private final JComponent myLeft;
    private final JComponent myRight;

    public CommonToolbarLayout(final JComponent left, final JComponent right) {
      myLeft = left;
      myRight = right;
    }

    public Dimension preferredLayoutSize(final Container parent) {

      Dimension size = new Dimension();
      Dimension leftSize = myLeft.getPreferredSize();
      Dimension rightSize = myRight.getPreferredSize();

      size.width = leftSize.width + rightSize.width;
      size.height = Math.max(leftSize.height, rightSize.height);

      return size;
    }

    public void layoutContainer(final Container parent) {
      Dimension size = parent.getSize();
      Dimension prefSize = parent.getPreferredSize();
      if (prefSize.width <= size.width) {
        myLeft.setBounds(0, 0, myLeft.getPreferredSize().width, parent.getHeight());
        Dimension rightSize = myRight.getPreferredSize();
        myRight.setBounds(parent.getWidth() - rightSize.width, 0, rightSize.width, parent.getHeight());
      }
      else {
        Dimension leftMinSize = myLeft.getMinimumSize();
        Dimension rightMinSize = myRight.getMinimumSize();

        int delta = (prefSize.width - size.width) / 2;

        myLeft.setBounds(0, 0, myLeft.getPreferredSize().width - delta, parent.getHeight());
        int rightX = (int)myLeft.getBounds().getMaxX();
        int rightWidth = size.width - rightX;
        if (rightWidth < rightMinSize.width) {
          Dimension leftSize = myLeft.getSize();
          int diffToRightMin = rightMinSize.width - rightWidth;
          if (leftSize.width - diffToRightMin >= leftMinSize.width) {
            leftSize.width -= diffToRightMin;
            myLeft.setSize(leftSize);
          }
        }

        myRight.setBounds((int)myLeft.getBounds().getMaxX(), 0, parent.getWidth() - myLeft.getWidth(), parent.getHeight());
      }

      toMakeVerticallyInCenter(myLeft, parent);
      toMakeVerticallyInCenter(myRight, parent);
    }

    private static void toMakeVerticallyInCenter(JComponent comp, Container parent) {
      final Rectangle compBounds = comp.getBounds();
      int compHeight = comp.getPreferredSize().height;
      final int parentHeight = parent.getHeight();
      if (compHeight > parentHeight) {
        compHeight = parentHeight;
      }

      int y = (int)Math.floor(parentHeight / 2.0 - compHeight / 2.0);
      comp.setBounds(compBounds.x, y, compBounds.width, compHeight);
    }
  }

  public IdeFocusManager getFocusManager() {
    return myFocusManager;
  }

  public RunnerLayoutUi getRunnerLayoutUi() {
    return myRunnerUi;
  }

  public String getName() {
    return mySessionName;
  }

  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();
    if (myLeftToolbarActions != null) {
      AnAction[] kids = myLeftToolbarActions.getChildren(null);
      ContainerUtil.addAll(result, kids);
    }
    return result;
  }

  public SwitchTarget getCurrentTarget() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return myTabs.getCurrentTarget();

    GridImpl grid = getSelectedGrid();
    if (grid != null && grid.getContents().size() <= 1) return myTabs.getCurrentTarget();

    if (grid != null) {
      SwitchTarget cell = grid.getCellFor(owner);
      return cell != null ? cell : myTabs.getCurrentTarget();
    }
    else {
      return myTabs.getCurrentTarget();
    }
  }

  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    List<SwitchTarget> result = new ArrayList<SwitchTarget>();

    result.addAll(myTabs.getTargets(true, false));
    GridImpl grid = getSelectedGrid();
    if (grid != null) {
      result.addAll(grid.getTargets(onlyVisible));
    }

    for (Wrapper wrapper : myMinimizedButtonsPlaceholder.values()) {
      if (!wrapper.isShowing()) continue;
      JComponent target = wrapper.getTargetComponent();
      if (target instanceof ActionToolbar) {
        ActionToolbar tb = (ActionToolbar)target;
        result.addAll(tb.getTargets(onlyVisible, false));
      }
    }

    return result;
  }


  private int findFreeWindow() {
    int i;
    for (i = 1; i < Integer.MAX_VALUE; i++) {
      if (!isUsed(i)) {
        return i;
      }
    }
    return i;
  }

  private boolean isUsed(int i) {
    for (RunnerContentUi child : myChildren) {
      if (child.getWindow() == i) {
        return true;
      }
    }
    return false;
  }

  private DockManagerImpl getDockManager() {
    return (DockManagerImpl)DockManager.getInstance(myProject);
  }
  
  class MyDragOutDelegate implements TabInfo.DragOutDelegate {
    private DragSession mySession;

    @Override
    public void dragOutStarted(MouseEvent mouseEvent, TabInfo info) {
      final JComponent component = info.getComponent();
      final Content[] data = CONTENT_KEY.getData((DataProvider)component);
      final List<Content> contents = Arrays.asList(data);

      storeDefaultIndices(data);

      final Dimension size = info.getComponent().getSize();
      final Image image = myTabs.getComponentImage(info);
      if (component instanceof Grid) {
        info.setHidden(true);
      }

      Presentation presentation = new Presentation(info.getText());
      presentation.setIcon(info.getIcon());
      mySession = getDockManager().createDragSession(mouseEvent, new DockableGrid(image, presentation,
                                                                                  size,
                                                                                  contents, 0));
    }

    @Override
    public void processDragOut(MouseEvent event, TabInfo source) {
      mySession.process(event);
    }

    @Override
    public void dragOutFinished(MouseEvent event, TabInfo source) {
      final Component component = event.getComponent();
      final IdeFrame window = UIUtil.getParentOfType(IdeFrame.class, component);
      if (window != null) {
        
      }
      mySession.process(event);
      mySession = null;
    }

    @Override
    public void dragOutCancelled(TabInfo source) {
      source.setHidden(false);
      mySession.cancel();
      mySession = null;
    }
  }

  class DockableGrid implements DockableContent<List<Content>> {
    final Image myImg;
    private Presentation myPresentation;
    private final Dimension myPreferredSize;
    private final List<Content> myContents;
    private final int myWindow;

    public DockableGrid(Image img, Presentation presentation, final Dimension size, List<Content> contents, int window) {
      myImg = img;
      myPresentation = presentation;
      myPreferredSize = size;
      myContents = contents;
      myWindow = window;
    }

    @Override
    public List<Content> getKey() {
      return myContents;
    }

    @Override
    public Image getPreviewImage() {
      return myImg;
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize;
    }

    @Override
    public String getDockContainerType() {
      return DockableGridContainerFactory.TYPE;
    }

    @Override
    public Presentation getPresentation() {
      return myPresentation;
    }

    public RunnerContentUi getRunnerUi() {
      return RunnerContentUi.this;
    }

    public RunnerContentUi getOriginalRunnerUi() {
      return myOriginal != null ? myOriginal : RunnerContentUi.this;
    }

    public List<Content> getContents() {
      return myContents;
    }

    @Override
    public void close() {
    }

    public int getWindow() {
      return myWindow;
    }
  }

  void fireContentOpened(Content content) {
    for (Listener each : myDockingListeners) {
      each.contentAdded(content);
    }
  }

  void fireContentClosed(Content content) {
    for (Listener each : myDockingListeners) {
      each.contentRemoved(content);
    }
  }
}
