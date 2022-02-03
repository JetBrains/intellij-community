// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.*;
import com.intellij.execution.ui.layout.actions.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.openapi.wm.impl.content.SelectContentStep;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SideBorder;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.TwoSideComponent;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.content.custom.options.CustomContentLayoutOptions;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.Activatable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import static com.intellij.ui.tabs.JBTabsEx.NAVIGATION_ACTIONS_KEY;

public final class RunnerContentUi implements ContentUI, Disposable, CellTransform.Facade, ViewContextEx, PropertyChangeListener,
                                              QuickActionProvider, DockContainer.Dialog, Activatable {
  public static final DataKey<RunnerContentUi> KEY = DataKey.create("DebuggerContentUI");
  public static final Key<Boolean> LIGHTWEIGHT_CONTENT_MARKER = Key.create("LightweightContent");

  @NonNls private static final String LAYOUT = "Runner.Layout";
  @NonNls private static final String SETTINGS = "XDebugger.Settings";
  @NonNls private static final String VIEW_POPUP = "Runner.View.Popup";
  @NonNls static final String VIEW_TOOLBAR = "Runner.View.Toolbar";

  private ShowDebugContentAction myShowDebugContentAction = null;

  private ContentManager myManager;
  private final RunnerLayout myLayoutSettings;

  private final @NotNull ActionManager myActionManager;
  private final @NlsSafe String mySessionName;
  private final String myRunnerId;
  private NonOpaquePanel myComponent;

  private final Wrapper myToolbar = new Wrapper();
  final MyDragOutDelegate myDragOutDelegate = new MyDragOutDelegate();

  JBRunnerTabsBase myTabs;
  private final Comparator<TabInfo> myTabsComparator = (o1, o2) -> {
    TabImpl tab1 = getTabFor(o1);
    TabImpl tab2 = getTabFor(o2);
    int index1 = tab1 != null ? tab1.getIndex() : -1;
    int index2 = tab2 != null ? tab2.getIndex() : -1;
    return index1 - index2;
  };
  private final Project myProject;

  private ActionGroup myTopLeftActions = new DefaultActionGroup();
  private ActionGroup myTopMiddleActions = new DefaultActionGroup();
  private ActionGroup myTopRightActions = new DefaultActionGroup();

  private final DefaultActionGroup myViewActions = new DefaultActionGroup();

  private final Map<GridImpl, Wrapper> myMinimizedButtonsPlaceholder = new HashMap<>();
  private final Map<GridImpl, TopToolbarWrappers> myCommonActionsPlaceholder = new HashMap<>();
  private final Map<GridImpl, TopToolbarContextActions> myContextActions = new HashMap<>();

  private boolean myUiLastStateWasRestored;

  private final Set<Object> myRestoreStateRequestors = new HashSet<>();
  private String myTopLeftActionsPlace = "RunnerContentUI.topLeftToolbar";
  private String myTopMiddleActionsPlace = "RunnerContentUI.topMiddleToolbar";
  private String myTopRightActionsPlace = "RunnerContentUI.topRightToolbar";
  private final IdeFocusManager myFocusManager;

  private boolean myMinimizeActionEnabled = true;
  private boolean myMoveToGridActionEnabled = true;
  private final RunnerLayoutUi myRunnerUi;

  private final Map<Pair<String, String>, LayoutAttractionPolicy> myAttractions = new HashMap<>();

  private ActionGroup myTabPopupActions;
  private ActionGroup myAdditionalFocusActions;

  private final ActionCallback myInitialized = new ActionCallback();
  private boolean myToDisposeRemovedContent = true;

  private int myAttractionCount;
  private ActionGroup myLeftToolbarActions;

  private boolean myContentToolbarBefore = true;
  private boolean myTopLeftActionsVisible = true;

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;
  private MyDropAreaPainter myCurrentPainter;
  private Disposable myGlassPaneListenersDisposable = Disposer.newDisposable();

  private RunnerContentUi myOriginal;
  private final CopyOnWriteArraySet<Listener> myDockingListeners = new CopyOnWriteArraySet<>();
  private final Set<RunnerContentUi> myChildren = new TreeSet<>(Comparator.comparingInt(o -> o.myWindow));
  private int myWindow;
  private boolean myDisposing;

  public RunnerContentUi(@NotNull Project project,
                         @NotNull RunnerLayoutUi ui,
                         @NotNull ActionManager actionManager,
                         @NotNull IdeFocusManager focusManager,
                         @NotNull RunnerLayout settings,
                         @NotNull String sessionName,
                         @NotNull String runnerId) {
    myProject = project;
    myRunnerUi = ui;
    myLayoutSettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;
    myFocusManager = focusManager;
    myRunnerId = runnerId;
  }

  public RunnerContentUi(@NotNull RunnerContentUi ui, @NotNull RunnerContentUi original, int window) {
    this(ui.myProject, ui.myRunnerUi, ui.myActionManager, ui.myFocusManager, ui.myLayoutSettings, ui.mySessionName, original.myRunnerId);
    myOriginal = original;
    original.myChildren.add(this);
    myWindow = window == 0 ? original.findFreeWindow() : window;
  }

  void setTopLeftActions(final @NotNull ActionGroup topActions, @NotNull String place) {
    myTopLeftActions = topActions;
    myTopLeftActionsPlace = place;

    rebuildCommonActions();
  }

  void setTopMiddleActions(final @NotNull ActionGroup topActions, @NotNull String place) {
    myTopMiddleActions = topActions;
    myTopMiddleActionsPlace = place;

    rebuildCommonActions();
  }

  void setTopRightActions(final @NotNull ActionGroup topActions, @NotNull String place) {
    myTopRightActions = topActions;
    myTopRightActionsPlace = place;

    rebuildCommonActions();
  }

  void setTabPopupActions(ActionGroup tabPopupActions) {
    myTabPopupActions = tabPopupActions;
    rebuildTabPopup();
  }

  void setAdditionalFocusActions(final ActionGroup group) {
    myAdditionalFocusActions = group;
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

  void setLeftToolbarVisible(boolean value) {
    myToolbar.setVisible(value);
    Border border = myTabs.getComponent().getBorder();
    if (border instanceof JBRunnerTabs.JBRunnerTabsBorder) {
      ((JBRunnerTabs.JBRunnerTabsBorder)border).setSideMask(value ? SideBorder.LEFT : SideBorder.NONE);
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  void setContentToolbarBefore(boolean value) {
    myContentToolbarBefore = value;
    for (GridImpl each : getGrids()) {
      each.setToolbarBefore(value);
    }

    myContextActions.clear();
    updateTabsUI(false);
  }

  void setTopLeftActionsVisible(boolean visible) {
    myTopLeftActionsVisible = visible;
    rebuildCommonActions();
  }

  private void initUi() {
    if (myTabs != null) return;

    myTabs = new JBRunnerTabs(myProject, this);
    myTabs.getComponent().setOpaque(false);
    myTabs.setDataProvider(dataId -> {
      if (ViewContext.CONTENT_KEY.is(dataId)) {
        TabInfo info = myTabs.getTargetInfo();
        if (info != null) {
          return getGridFor(info).getData(dataId);
        }
      }
      else if (ViewContext.CONTEXT_KEY.is(dataId)) {
        return this;
      }
      return null;
    });
    myTabs.getPresentation()
      .setTabLabelActionsAutoHide(false).setInnerInsets(JBInsets.emptyInsets())
      .setToDrawBorderIfTabsHidden(false).setTabDraggingEnabled(isMoveToGridActionEnabled()).setUiDecorator(null);
    rebuildTabPopup();

    myTabs.getPresentation().setPaintFocus(false).setRequestFocusOnLastFocusedComponent(true);

    NonOpaquePanel wrapper = new MyComponent();
    wrapper.add(myToolbar, BorderLayout.WEST);
    wrapper.add(myTabs.getComponent(), BorderLayout.CENTER);
    wrapper.setBorder(JBUI.Borders.emptyTop(-1));

    myComponent = wrapper;

    myTabs.addListener(new TabsListener() {

      @Override
      public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        if (oldSelection != null && !isStateBeingRestored()) {
          final GridImpl grid = getGridFor(oldSelection);
          if (grid != null && getTabFor(grid) != null) {
            grid.saveUiState();
          }
        }
      }

      @Override
      public void tabsMoved() {
        saveUiState();
      }

      @Override
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
      public void mousePressed(@NotNull MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          final TabInfo tabInfo = myTabs.findInfo(e);
          final GridImpl grid = tabInfo == null ? null : getGridFor(tabInfo);
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
    }
    else {
      DockManager dockManager = myProject.isDefault() ? null : DockManager.getInstance(myProject);
      if (dockManager != null) {
        dockManager.register(this, this);
      }
    }
    if (Registry.is("debugger.new.tool.window.layout")) {
      MouseAdapter adapter = new MouseAdapter() {
        private Point myPressPoint = null;

        @Override
        public void mousePressed(MouseEvent e) {
          ObjectUtils.consumeIfNotNull(InternalDecoratorImpl.Companion.findTopLevelDecorator(myComponent),
                                       decorator -> decorator.activate(ToolWindowEventSource.ToolWindowHeader));
          myPressPoint = e.getPoint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
            ObjectUtils.consumeIfNotNull(InternalDecoratorImpl.Companion.findTopLevelDecorator(myComponent),
                                         decorator -> {
                                           if (decorator.isHeaderVisible()) return;
                                           String id = decorator.getToolWindowId();
                                           ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(myProject);
                                           ToolWindow window = manager.getToolWindow(id);
                                           ObjectUtils.consumeIfNotNull(window, w -> manager.setMaximized(w, !manager.isMaximized(w)));
                                         });
          }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          InternalDecoratorImpl decorator = InternalDecoratorImpl.Companion.findTopLevelDecorator(myComponent);
          if (decorator == null || decorator.isHeaderVisible()) return;
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(decorator.getToolWindowId());
          ToolWindowAnchor anchor = window != null ? window.getAnchor() : null;
          if (anchor == ToolWindowAnchor.BOTTOM && SwingUtilities.isLeftMouseButton(e) && myPressPoint != null) {
            ThreeComponentsSplitter splitter = ComponentUtil.getParentOfType(ThreeComponentsSplitter.class, myComponent);
            if (splitter != null &&
                splitter.getOrientation() &&
                SwingUtilities.isDescendingFrom(getComponent(), splitter.getLastComponent())) {
              int size = splitter.getLastSize();
              int yDiff = myPressPoint.y - e.getPoint().y;
              splitter.setLastSize(size + yDiff);
            }
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          myPressPoint = null;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          InternalDecoratorImpl decorator = InternalDecoratorImpl.Companion.findTopLevelDecorator(myComponent);
          if (decorator == null || decorator.isHeaderVisible()) {
            e.getComponent().setCursor(Cursor.getDefaultCursor());
            return;
          }
          e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        }

        @Override
        public void mouseExited(MouseEvent e) {
          e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
      };
      wrapper.addMouseListener(adapter);
      myTabs.getComponent().addMouseListener(adapter);
      wrapper.addMouseMotionListener(adapter);
      myTabs.getComponent().addMouseMotionListener(adapter);
    }
    updateRestoreLayoutActionVisibility();
  }

  private void rebuildTabPopup() {
    initUi();

    myTabs.setPopupGroup(getCellPopupGroup(TAB_POPUP_PLACE), TAB_POPUP_PLACE, true);

    for (GridImpl each : getGrids()) {
      each.rebuildTabPopup();
    }
  }

  @Override
  public ActionGroup getCellPopupGroup(final String place) {
    final ActionGroup original = myTabPopupActions != null ? myTabPopupActions : (ActionGroup)myActionManager.getAction(VIEW_POPUP);
    final ActionGroup focusPlaceholder = (ActionGroup)myActionManager.getAction("Runner.Focus");

    DefaultActionGroup group = new DefaultActionGroup(VIEW_POPUP, original.isPopup());

    if (myShowDebugContentAction == null && "Debug".equals(myRunnerId)) {
      myShowDebugContentAction = new ShowDebugContentAction(this, myTabs.getComponent(), this);
    }
    if (myShowDebugContentAction != null) {
      group.add(myShowDebugContentAction);
    }

    AnActionEvent event =
      new AnActionEvent(null, DataManager.getInstance().getDataContext(), place, new Presentation(), ActionManager.getInstance(), 0);
    for (AnAction each : original.getChildren(event)) {
      if (each == focusPlaceholder) {
        final AnAction[] focusActions = ((ActionGroup)each).getChildren(event);
        for (AnAction eachFocus : focusActions) {
          group.add(eachFocus);
        }
        if (myAdditionalFocusActions != null) {
          for (AnAction action : myAdditionalFocusActions.getChildren(event)) {
            group.add(action);
          }
        }
      }
      else {
        group.add(each);
      }
    }
    if (myViewActions.getChildrenCount() > 0) {
      DefaultActionGroup layoutGroup = new DefaultActionGroup(myViewActions.getChildren(null));
      layoutGroup.getTemplatePresentation().setText(ExecutionBundle.messagePointer("action.presentation.RunnerContentUi.text"));
      layoutGroup.setPopup(true);
      group.addSeparator();
      group.addAction(layoutGroup);
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

  @NotNull
  public JBTabs getTabs() { return myTabs; }

  public AnAction @NotNull[] getViewActions() {
    return myViewActions.getChildren(null);
  }

  @Override
  public void propertyChange(final @NotNull PropertyChangeEvent evt) {
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
             || Content.PROP_PINNED.equals(property)
             || Content.PROP_ACTIONS.equals(property)
             || Content.PROP_DESCRIPTION.equals(property)
             || Content.PROP_TAB_COLOR.equals(property)) {
      cell.updateTabPresentation(content);
      updateTabsUI(false);
    }
  }

  void processBounce(Content content, final boolean activate) {
    final GridImpl grid = getGridFor(content, false);
    if (grid == null) return;

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
      size = JBUI.size(200, 200);
    }

    DockableGrid content = new DockableGrid(null, new Presentation(), size, Arrays.asList(contents), window);
    if (target != null) {
      target.add(content, null);
    }
    else {
      Point location = gridCell.getLocation();
      if (location == null) {
        location = getComponent().getLocationOnScreen();
      }
      location.translate(size.width / 2, size.height / 2);
      getDockManager().createNewDockContainerFor(content, new RelativePoint(location));
    }
    return ActionCallback.DONE;
  }

  private void storeDefaultIndices(Content @NotNull [] contents) {
    //int i = 0;
    for (Content content : contents) {
      content.putUserData(RunnerLayout.DEFAULT_INDEX, getStateFor(content).getTab().getDefaultIndex());
      //content.putUserData(CONTENT_NUMBER, i++);
    }
  }

  @Override
  public @NotNull RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(myTabs.getComponent());
  }

  @Override
  public @NotNull ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    if (!(content instanceof DockableGrid)) {
      return ContentResponse.DENY;
    }
    final RunnerContentUi ui = ((DockableGrid)content).getOriginalRunnerUi();
    return ui.getProject() == myProject && ui.mySessionName.equals(mySessionName) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
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
  public void add(@NotNull DockableContent dockable, RelativePoint dropTarget) {
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
          view.setPlaceInGrid(calcPlaceInGrid(point, myComponent.getSize()));
        }
        else if (contents.size() == 1 && !wasRestoring) {
          view.assignTab(null);
          view.setPlaceInGrid(myLayoutSettings.getDefaultGridPlace(content));
        }
        view.setWindow(myWindow);
        myManager.addContent(content);
      }
    }
    finally {
      setStateIsBeingRestored(false, this);
    }

    saveUiState();

    updateTabsUI(true);
  }

  @Override
  public void closeAll() {
    final Content[] contents = myManager.getContents();
    if (myOriginal != null) {
      for (Content content : contents) {
        getStateFor(content).setWindow(0);
        myOriginal.myManager.addContent(content);
        GridCell cell = myOriginal.findCellFor(content);
        if (cell != null) {
          myOriginal.restore(content);
          cell.minimize(content);
        }
      }
    }
    myManager.removeAllContents(false);
  }

  @Override
  public void addListener(@NotNull Listener listener, Disposable parent) {
    myDockingListeners.add(listener);
    Disposer.register(parent, () -> myDockingListeners.remove(listener));
  }

  @Override
  public boolean isEmpty() {
    return myTabs.isEmptyVisible() || myDisposing;
  }

  @Override
  public Image processDropOver(@NotNull DockableContent dockable, RelativePoint dropTarget) {
    JBTabs current = getTabsAt(dockable, dropTarget);
    if (myCurrentOver != null && myCurrentOver != current) {
      resetDropOver(dockable);
    }

    if (myCurrentOver == null && current != null) {
      myCurrentOver = current;
      Presentation presentation = dockable.getPresentation();
      myCurrentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      myCurrentOverImg = myCurrentOver.startDropOver(myCurrentOverInfo, dropTarget);
    }

    if (myCurrentOver != null) {
      myCurrentOver.processDropOver(myCurrentOverInfo, dropTarget);
    }

    if (myCurrentPainter == null) {
      myCurrentPainter = new MyDropAreaPainter();
      myGlassPaneListenersDisposable = Disposer.newDisposable("GlassPaneListeners");
      Disposer.register(this, myGlassPaneListenersDisposable);
      IdeGlassPaneUtil.find(myComponent).addPainter(myComponent, myCurrentPainter, myGlassPaneListenersDisposable);
    }
    myCurrentPainter.processDropOver(this, dockable, dropTarget);

    return myCurrentOverImg;
  }

  public void toggleContentPopup(JBTabs tabs) {
    if (myOriginal != null) {
      myOriginal.toggleContentPopup(tabs);
      return;
    }

    List<Content> contents = getPopupContents();
    final SelectContentStep step = new SelectContentStep(contents);
    final Content selectedContent = myManager.getSelectedContent();
    if (selectedContent != null) {
      step.setDefaultOptionIndex(myManager.getIndexOfContent(selectedContent));
    }

    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    popup.showUnderneathOf(tabs.getTabLabel(tabs.getSelectedInfo()));

    if (selectedContent instanceof TabbedContent) {
      new Alarm(Alarm.ThreadToUse.SWING_THREAD, popup).addRequest(() -> popup.handleSelect(false), 50);
    }
  }

  public List<Content> getPopupContents() {
    if (myOriginal != null) return myOriginal.getPopupContents();

    List<Content> contents = new ArrayList<>(Arrays.asList(myManager.getContents()));
    myChildren.stream()
      .flatMap(child -> Arrays.stream(child.myManager.getContents()))
      .forEachOrdered(contents::add);

    RunContentManager contentManager = RunContentManager.getInstance(myProject);
    RunContentDescriptor selectedDescriptor = contentManager.getSelectedContent();
    Content selectedContent;
    if (selectedDescriptor != null) {
      selectedContent = selectedDescriptor.getAttachedContent();
    }
    else {
      selectedContent = null;
    }

    Content[] debugTabs = ToolWindowManager.getInstance(myProject)
      .getToolWindow(ToolWindowId.DEBUG)
      .getContentManager().getContents();
    for (Content content : debugTabs) {
      if (content != selectedContent) {
        contents.add(content);
      }
    }
    return contents;
  }

  private static @NotNull PlaceInGrid calcPlaceInGrid(Point point, Dimension size) {
    // 1/3 (left) |   (center/bottom) | 1/3 (right)
    if (point.x < size.width / 3) return PlaceInGrid.left;
    if (point.x > size.width * 2 / 3) return PlaceInGrid.right;

    // 3/4 (center with tab titles) | 1/4 (bottom)
    if (point.y > size.height * 3 / 4) return PlaceInGrid.bottom;

    return PlaceInGrid.center;
  }

  private @Nullable JBTabs getTabsAt(DockableContent content, RelativePoint point) {
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
  public void resetDropOver(@NotNull DockableContent content) {
    if (myCurrentOver != null) {
      myCurrentOver.resetDropOver(myCurrentOverInfo);
      myCurrentOver = null;
      myCurrentOverInfo = null;
      myCurrentOverImg = null;

      Disposer.dispose(myGlassPaneListenersDisposable);
      myGlassPaneListenersDisposable = Disposer.newDisposable();
      myCurrentPainter = null;
    }
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return myOriginal != null;
  }

  @Override
  public void setManager(final @NotNull ContentManager manager) {
    assert myManager == null;

    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(final @NotNull ContentManagerEvent event) {
        initUi();
        if (event.getContent().getUserData(LIGHTWEIGHT_CONTENT_MARKER) == Boolean.TRUE) {
          myLayoutSettings.setLightWeight(event.getContent());
          Disposer.register(event.getContent(), () -> myLayoutSettings.clearStateFor(event.getContent()));
        }


        GridImpl grid = getGridFor(event.getContent(), true);
        if (grid == null) {
          return;
        }

        grid.add(event.getContent());

        if (getSelectedGrid() == grid) {
          grid.processAddToUi(false);
        }

        if (myManager.getComponent().isShowing() && !isStateBeingRestored()) {
          setStateIsBeingRestored(true, RunnerContentUi.this);
          try {
            grid.restoreLastUiState();
          } finally {
            setStateIsBeingRestored(false, RunnerContentUi.this);
          }
        }

        updateTabsUI(false);

        event.getContent().addPropertyChangeListener(RunnerContentUi.this);
        fireContentOpened(event.getContent());
        if (myMinimizeActionEnabled) {
          AnAction[] actions = myViewActions.getChildren(null);
          for (AnAction action : actions) {
            if (action instanceof ViewLayoutModificationAction && ((ViewLayoutModificationAction)action).getContent() == event.getContent()) return;
          }

          CustomContentLayoutOptions layoutOptions = event.getContent().getUserData(CustomContentLayoutOptions.KEY);
          AnAction viewAction = layoutOptions != null && layoutOptions.getAvailableOptions().length > 0 ?
                                new ViewLayoutModeActionGroup(RunnerContentUi.this, event.getContent()) :
                                new RestoreViewAction(RunnerContentUi.this, event.getContent());
          myViewActions.addAction(viewAction).setAsSecondary(true);

          List<AnAction> toAdd = new ArrayList<>();
          for (AnAction anAction : myViewActions.getChildren(null)) {
            if (!(anAction instanceof ViewLayoutModificationAction)) {
              myViewActions.remove(anAction);
              toAdd.add(anAction);
            }
          }
          for (AnAction anAction : toAdd) {
            myViewActions.addAction(anAction).setAsSecondary(true);
          }
        }
      }

      @Override
      public void contentRemoved(final @NotNull ContentManagerEvent event) {
        final Content content = event.getContent();
        content.removePropertyChangeListener(RunnerContentUi.this);

        GridImpl grid = (GridImpl)findGridFor(content);
        if (grid != null) {
          grid.remove(content);
          if (grid.isEmpty()) {
            grid.processRemoveFromUi();
          }
          removeGridIfNeeded(grid);
        }
        updateTabsUI(false);
        fireContentClosed(content);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (Disposer.isDisposed(content)) {
            AnAction[] actions = myViewActions.getChildren(null);
            for (AnAction action : actions) {
              if (action instanceof ViewLayoutModificationAction && ((ViewLayoutModificationAction)action).getContent() == content) {
                myViewActions.remove(action);
                break;
              }
            }
          }
        });
      }

      @Override
      public void selectionChanged(final @NotNull ContentManagerEvent event) {
        if (isStateBeingRestored()) return;

        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          select(event.getContent(), false);
        }
      }
    });
  }

  private @Nullable GridImpl getSelectedGrid() {
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

  private @Nullable GridImpl getGridFor(@NotNull Content content, boolean createIfMissing) {
    GridImpl grid = (GridImpl)findGridFor(content);
    if (grid != null || !createIfMissing) {
      return grid;
    }

    grid = new GridImpl(this, mySessionName);
    grid.setToolbarBefore(myContentToolbarBefore);

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

    TabInfo tab = new TabInfo(grid).setObject(getStateFor(content).getTab()).setText(ExecutionBundle.message("runner.context.tab"));

    Wrapper leftWrapper = new Wrapper();
    Wrapper middleWrapper = new Wrapper();
    Wrapper rightWrapper = new Wrapper();
    myCommonActionsPlaceholder.put(grid, new TopToolbarWrappers(leftWrapper, middleWrapper, rightWrapper));

    Wrapper minimizedToolbar = new Wrapper();
    myMinimizedButtonsPlaceholder.put(grid, minimizedToolbar);


    final Wrapper searchComponent = new Wrapper();
    if (content.getSearchComponent() != null) {
      searchComponent.setContent(content.getSearchComponent());
    }

    TwoSideComponent right = new TwoSideComponent(searchComponent, minimizedToolbar);


    NonOpaquePanel sideComponent = new TwoSideComponent(leftWrapper, new TwoSideComponent(middleWrapper, new TwoSideComponent(right, rightWrapper)));
    sideComponent.setVisible(!myTabs.isHideTopPanel());
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

  ActionGroup getSettingsActions() {
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
    return ArrayUtil.contains(content, manager.getContents());
  }

  private static void moveFollowingTabs(int index, final JBTabs tabs) {
    for (TabInfo info : tabs.getTabs()) {
      TabImpl tab = getTabFor(info);
      if (tab != null) {
        int tabIndex = tab.getIndex();
        if (tabIndex >= index) {
          tab.setIndex(tabIndex + 1);
        }
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

  @Override
  public @Nullable GridCell findCellFor(final @NotNull Content content) {
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
    for (Map.Entry<GridImpl, TopToolbarWrappers> entry : myCommonActionsPlaceholder.entrySet()) {
      Wrapper leftPlaceHolder = entry.getValue().left;
      Wrapper middlePlaceHolder = entry.getValue().middle;
      Wrapper rightPlaceHolder = entry.getValue().right;

      TopToolbarContextActions topToolbarContextActions = myContextActions.get(entry.getKey());

      DefaultActionGroup leftGroupToBuild = new DefaultActionGroup();
      if (myTopLeftActionsVisible) {
        leftGroupToBuild.addAll(myTopLeftActions);
      }
      final AnAction[] leftActions = leftGroupToBuild.getChildren(null);

      if (topToolbarContextActions == null || !Arrays.equals(leftActions, topToolbarContextActions.left)) {
        setActions(leftPlaceHolder, myTopLeftActionsPlace, leftGroupToBuild);
      }

      DefaultActionGroup middleGroupToBuild = new DefaultActionGroup();
      middleGroupToBuild.addAll(myTopMiddleActions);
      final AnAction[] middleActions = middleGroupToBuild.getChildren(null);

      if (topToolbarContextActions == null || !Arrays.equals(middleActions, topToolbarContextActions.middle)) {
        setActions(middlePlaceHolder, myTopMiddleActionsPlace, middleGroupToBuild);
      }

      DefaultActionGroup rightGroupToBuild = new DefaultActionGroup();
      rightGroupToBuild.addAll(myTopRightActions);
      final AnAction[] rightActions = rightGroupToBuild.getChildren(null);

      if (topToolbarContextActions == null || !Arrays.equals(rightActions, topToolbarContextActions.right)) {
        setActions(rightPlaceHolder, myTopRightActionsPlace, rightGroupToBuild);
      }

      myContextActions.put(entry.getKey(), new TopToolbarContextActions(leftActions, middleActions, rightActions));

      if (leftGroupToBuild.getChildrenCount() > 0 || rightGroupToBuild.getChildrenCount() > 0) {
        hasToolbarContent = true;
      }
    }

    return hasToolbarContent;
  }

  private void setActions(@NotNull Wrapper placeHolder, @NotNull String place, @NotNull DefaultActionGroup group) {
    ActionToolbar tb = myActionManager.createActionToolbar(place, group, true);
    tb.setReservePlaceAutoPopupIcon(false);
    // see IDEA-262878, evaluate action on the toolbar should get the editor data context
    tb.setTargetComponent(Registry.is("debugger.new.tool.window.layout") ? myComponent : null);
    tb.getComponent().setBorder(null);
    tb.getComponent().setOpaque(false);

    placeHolder.setContent(tb.getComponent());
  }

  private boolean rebuildMinimizedActions() {
    for (Map.Entry<GridImpl, Wrapper> entry : myMinimizedButtonsPlaceholder.entrySet()) {
      Wrapper eachPlaceholder = entry.getValue();
      ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.RUNNER_LAYOUT_BUTTON_TOOLBAR, myViewActions, true);
      tb.setSecondaryActionsIcon(AllIcons.Debugger.RestoreLayout, Registry.is("debugger.new.tool.window.layout"));
      tb.setSecondaryActionsTooltip(ExecutionBundle.message("runner.content.tooltip.layout.settings"));
      tb.setTargetComponent(myComponent);
      tb.getComponent().setOpaque(false);
      tb.getComponent().setBorder(null);
      tb.setReservePlaceAutoPopupIcon(false);
      JComponent minimized = tb.getComponent();
      eachPlaceholder.setContent(minimized);
    }

    myTabs.getComponent().revalidate();
    myTabs.getComponent().repaint();

    return myViewActions.getChildrenCount() > 0;
  }

  private void updateTabsUI(final boolean validateNow) {
    boolean hasToolbarContent = rebuildToolbar();

    Set<String> usedNames = new HashSet<>();
    List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      hasToolbarContent |= updateTabUI(each, usedNames);
    }
    int tabsCount = tabs.size() + myChildren.stream().mapToInt(child -> child.myTabs.getTabCount()).sum();
    myTabs.getPresentation().setHideTabs(!hasToolbarContent && tabsCount <= 1 && myOriginal == null);
    myTabs.updateTabActions(validateNow);

    if (validateNow) {
      myTabs.sortTabs(myTabsComparator);
    }
  }

  private boolean updateTabUI(TabInfo tab, Set<? super String> usedNames) {
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
      }
      else {
        title = StringUtil.join(contents, Content::getTabName, " | ");
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
    if (isStateBeingRestored()) return ActionCallback.REJECTED;

    try {
      setStateIsBeingRestored(true, this);

      List<TabInfo> tabs = new ArrayList<>(myTabs.getTabs());

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

  @Override
  public void saveUiState() {
    if (isStateBeingRestored()) return;

    if (myOriginal != null) {
      myOriginal.saveUiState();
      return;
    }
    if (!myUiLastStateWasRestored) return;

    int offset = updateTabsIndices(myTabs, 0);
    for (RunnerContentUi child : myChildren) {
      offset = updateTabsIndices(child.myTabs, offset);
    }

    doSaveUiState();
  }

  private static int updateTabsIndices(final JBTabs tabs, int offset) {
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

  @Override
  public @Nullable Tab getTabFor(final Grid grid) {
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

  private static @Nullable TabImpl getTabFor(final @Nullable TabInfo tab) {
    if (tab == null) {
      return null;
    }
    return (TabImpl)tab.getObject();
  }

  private static GridImpl getGridFor(TabInfo tab) {
    return (GridImpl)tab.getComponent();
  }

  @Override
  public @Nullable Grid findGridFor(@NotNull Content content) {
    TabImpl tab = (TabImpl)getStateFor(content).getTab();
    for (TabInfo each : myTabs.getTabs()) {
      TabImpl t = getTabFor(each);
      if (t != null && t.equals(tab)) return getGridFor(each);
    }

    return null;
  }

  private @NotNull List<GridImpl> getGrids() {
    return ContainerUtil.map(myTabs.getTabs(), RunnerContentUi::getGridFor);
  }

  @Override
  public boolean isSingleSelection() {
    return false;
  }

  @Override
  public boolean isToSelectAddedContent() {
    return false;
  }

  @Override
  public boolean canBeEmptySelection() {
    return true;
  }

  @Override
  public void beforeDispose() {
    if (myOriginal != null) {
      myDisposing = true;
      fireContentClosed(null);
    }
  }

  @Override
  public boolean canChangeSelectionTo(@NotNull Content content, boolean implicit) {
    if (implicit) {
      GridImpl grid = getGridFor(content, false);
      if (grid != null) {
        return !grid.isMinimized(content);
      }
    }

    return true;
  }

  @Override
  public @NotNull String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @Override
  public @NotNull String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  @Override
  public @NotNull String getPreviousContentActionName() {
    return ExecutionBundle.message("select.previous.tab");
  }

  @Override
  public @NotNull String getNextContentActionName() {
    return ExecutionBundle.message("select.next.tab");
  }

  @Override
  public void dispose() {
    if (myOriginal != null) {
      myOriginal.myChildren.remove(this);
    }
    myMinimizedButtonsPlaceholder.clear();
    myCommonActionsPlaceholder.clear();
    myContextActions.clear();

    myOriginal = null;
    myTopLeftActions = null;
    myTopRightActions = null;
    myAdditionalFocusActions = null;
    myLeftToolbarActions = null;
  }

  @Override
  public void restoreLayout() {
    final RunnerContentUi[] children = myChildren.toArray(new RunnerContentUi[0]);
    final LinkedHashSet<Content> contents = new LinkedHashSet<>();
    Collections.addAll(contents, myManager.getContents());
    for (RunnerContentUi child : children) {
      Collections.addAll(contents, child.myManager.getContents());
    }
    for (AnAction action : myViewActions.getChildren(null)) {
      if (!(action instanceof ViewLayoutModificationAction)) continue;
      contents.add(((ViewLayoutModificationAction)action).getContent());
    }
    Content[] all = contents.toArray(new Content[0]);
    Arrays.sort(all, Comparator.comparingInt(content -> getStateFor(content).getTab().getDefaultIndex()));

    setStateIsBeingRestored(true, this);
    try {
      for (RunnerContentUi child : children) {
        child.myManager.removeAllContents(false);
      }
      myManager.removeAllContents(false);
    }
    finally {
      setStateIsBeingRestored(false, this);
    }

    myLayoutSettings.resetToDefault();
    for (Content each : all) {
      myManager.addContent(each);
      CustomContentLayoutOptions customLayoutOptions = each.getUserData(CustomContentLayoutOptions.KEY);
      if (customLayoutOptions != null) {
        customLayoutOptions.restore();
      }
    }

    updateTabsUI(true);
  }

  @Override
  public boolean isStateBeingRestored() {
    return !myRestoreStateRequestors.isEmpty();
  }

  @Override
  public void setStateIsBeingRestored(final boolean restoredNow, final Object requestor) {
    if (restoredNow) {
      myRestoreStateRequestors.add(requestor);
    }
    else {
      myRestoreStateRequestors.remove(requestor);
    }
  }

  ActionGroup getLayoutActions() {
    return (ActionGroup)myActionManager.getAction(LAYOUT);
  }

  public void updateActionsImmediately() {
    Collection<TopToolbarWrappers> values = myCommonActionsPlaceholder.values();
    Stream<Wrapper> leftWrappers = values.stream().map(it -> it.left);
    Stream<Wrapper> rightWrappers = values.stream().map(it -> it.right);
    StreamEx.of(myToolbar).append(leftWrappers).append(rightWrappers)
      .map(Wrapper::getTargetComponent)
      .select(ActionToolbar.class)
      .distinct()
      .forEach(ActionToolbar::updateActionsImmediately);
  }

  void setMinimizeActionEnabled(final boolean enabled) {
    myMinimizeActionEnabled = enabled;
    updateRestoreLayoutActionVisibility();
  }

  private void updateRestoreLayoutActionVisibility() {
    List<AnAction> specialActions = new ArrayList<>();
    for (AnAction action : myViewActions.getChildren(null)) {
      if (!(action instanceof ViewLayoutModificationAction)) specialActions.add(action);
    }
    if (myMinimizeActionEnabled) {
      if (specialActions.isEmpty()) {
        myViewActions.addAction(new Separator()).setAsSecondary(true);
        myViewActions.addAction(ActionManager.getInstance().getAction("Runner.RestoreLayout")).setAsSecondary(true);
      }
    }
    else {
      for (AnAction action : specialActions) {
        myViewActions.remove(action);
      }
    }
  }

  void setMovetoGridActionEnabled(final boolean enabled) {
    myMoveToGridActionEnabled = enabled;
    if (myTabs != null) {
      myTabs.getPresentation().setTabDraggingEnabled(enabled);
    }
  }

  @Override
  public boolean isMinimizeActionEnabled() {
    return myMinimizeActionEnabled && myOriginal == null;
  }

  @Override
  public boolean isMoveToGridActionEnabled() {
    return myMoveToGridActionEnabled;
  }

  public void setPolicy(String contentId, final LayoutAttractionPolicy policy) {
    myAttractions.put(Pair.create(contentId, null), policy);
  }

  void setConditionPolicy(final String condition, final LayoutAttractionPolicy policy) {
    myAttractions.put(Pair.create(null, condition), policy);
  }

  private static @NotNull LayoutAttractionPolicy getOrCreatePolicyFor(@Nullable String contentId, @Nullable String condition,
                                                                      @NotNull Map<Pair<String, String>, LayoutAttractionPolicy> map,
                                                                      LayoutAttractionPolicy defaultPolicy) {
    LayoutAttractionPolicy policy = map.putIfAbsent(Pair.create(contentId, condition), defaultPolicy);
    return policy != null ? policy : defaultPolicy;
  }

  public @Nullable Content findContent(@NotNull String key) {
    final ContentManager manager = getContentManager();
    if (manager == null) return null;

    Content[] contents = manager.getContents();
    for (Content content : contents) {
      String kind = content.getUserData(ViewImpl.ID);
      if (key.equals(kind)) {
        return content;
      }
    }

    return null;
  }

  private @Nullable Content findMinimizedContent(@NotNull String key) {
    for (AnAction action : myViewActions.getChildren(null)) {
      if (!(action instanceof ViewLayoutModificationAction)) continue;

      Content content = ((ViewLayoutModificationAction)action).getContent();
      if (key.equals(content.getUserData(ViewImpl.ID))) {
        return content;
      }
    }

    return null;
  }

  public @Nullable Content findOrRestoreContentIfNeeded(@NotNull String key) {
    Content content = findContent(key);
    if (content == null) {
      content = findMinimizedContent(key);
      if (content != null) {
        restore(content);
      }
    }
    return content;
  }

  void setToDisposeRemovedContent(final boolean toDispose) {
    myToDisposeRemovedContent = toDispose;
  }

  @Override
  public boolean isToDisposeRemovedContent() {
    return myToDisposeRemovedContent;
  }

  private static class MyDropAreaPainter extends AbstractPainter {
    private Shape myBoundingBox;

    @Override
    public boolean needsRepaint() {
      return myBoundingBox != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (myBoundingBox == null) return;
      GraphicsUtil.setupAAPainting(g);
      g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
      g.fill(myBoundingBox);
    }

    private void processDropOver(RunnerContentUi ui, DockableContent dockable, RelativePoint dropTarget) {
      myBoundingBox = null;
      setNeedsRepaint(true);

      if (!(dockable instanceof DockableGrid)) return;

      JComponent component = ui.myComponent;
      Point point = dropTarget != null ? dropTarget.getPoint(component) : null;

      // do not paint anything if adding to the top
      if (ui.myTabs.shouldAddToGlobal(point)) return;

      // calc target place-in-grid
      PlaceInGrid targetPlaceInGrid = null;
      for (Content c : ((DockableGrid)dockable).getContents()) {
        View view = ui.getStateFor(c);
        if (view.isMinimizedInGrid()) continue;
        PlaceInGrid defaultGridPlace = ui.getLayoutSettings().getDefaultGridPlace(c);
        targetPlaceInGrid = point == null ? defaultGridPlace : calcPlaceInGrid(point, component.getSize());
        break;
      }
      if (targetPlaceInGrid == null) return;

      // calc the default rectangle for the targetPlaceInGrid "area"
      Dimension size = component.getSize();
      Rectangle r = new Rectangle(size);
      switch (targetPlaceInGrid) {
        case left:
          r.width /= 3;
          break;
        case center:
          r.width /= 3;
          r.x += r.width;
          break;
        case right:
          r.width /= 3;
          r.x += 2 * r.width;
          break;
        case bottom:
          r.height /= 4;
          r.y += 3 * r.height;
          break;
      }
      // adjust the rectangle if the target grid cell is already present and showing
      for (Content c : ui.getContentManager().getContents()) {
        GridCell cellFor = ui.findCellFor(c);
        PlaceInGrid placeInGrid = cellFor == null ? null : ((GridCellImpl)cellFor).getPlaceInGrid();
        if (placeInGrid != targetPlaceInGrid) continue;
        Wrapper wrapper = ComponentUtil.getParentOfType((Class<? extends Wrapper>)Wrapper.class, (Component)c.getComponent());
        JComponent cellWrapper = wrapper == null ? null : (JComponent)wrapper.getParent();
        if (cellWrapper == null || !cellWrapper.isShowing()) continue;
        r = new RelativeRectangle(cellWrapper).getRectangleOn(component);
        break;
      }
      myBoundingBox = new Rectangle2D.Double(r.x, r.y, r.width, r.height);
    }
  }

  private class MyComponent extends NonOpaquePanel implements DataProvider, QuickActionProvider {
    private boolean myWasEverAdded;

    MyComponent() {
      super(new BorderLayout());
      setOpaque(true);
      setFocusCycleRoot(!ScreenReader.isActive());
      setBorder(new ToolWindowEx.Border(false, false, false, false));

    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (!Registry.is("debugger.new.tool.window.layout")) return;
      InternalDecoratorImpl decorator = ComponentUtil.getParentOfType(InternalDecoratorImpl.class, myComponent);
      if (decorator != null && myTabs.getTabCount() > 0 && !decorator.isHeaderVisible()) {
        UIUtil.drawHeader(g, 0, getWidth(), decorator.getHeaderHeight(), decorator.isActive(), true, false, false);
      }
    }

    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
      if (QuickActionProvider.KEY.is(dataId)) {
        return RunnerContentUi.this;
      }

      if (CloseAction.CloseTarget.KEY.is(dataId)) {
        Content content = getContentManager().getSelectedContent();
        if (content != null && content.isCloseable()) {
          ContentManager contentManager = Objects.requireNonNull(content.getManager());
          if (contentManager.canCloseContents()) {
            return (CloseAction.CloseTarget)() -> contentManager.removeContent(content, true, true, true);
          }
        }
      }

      ContentManager originalContentManager = myOriginal == null ? null : myOriginal.getContentManager();
      JComponent originalContentComponent = originalContentManager == null ? null : originalContentManager.getComponent();
      if (originalContentComponent instanceof DataProvider) {
        return ((DataProvider)originalContentComponent).getData(dataId);
      }
      return null;
    }

    @Override
    public @NotNull String getName() {
      return RunnerContentUi.this.getName();
    }

    @Override
    public @NotNull List<AnAction> getActions(boolean originalProvider) {
      return RunnerContentUi.this.getActions(originalProvider);
    }

    @Override
    public JComponent getComponent() {
      return RunnerContentUi.this.getComponent();
    }

    @Override
    public boolean isCycleRoot() {
      return RunnerContentUi.this.isCycleRoot();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (null !=
          ComponentUtil.findParentByCondition(this, component -> UIUtil.isClientPropertyTrue(component, ToolWindowsPane.TEMPORARY_ADDED))) {
        return;
      }

      if (!myUiLastStateWasRestored && myOriginal == null) {
        myUiLastStateWasRestored = true;

        // [kirillk] this is done later since restoreUiState doesn't work properly in the addNotify call chain
        //todo to investigate and to fix (may cause extra flickering)
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> restoreLastUiState().doWhenDone(() -> {
          if (!myWasEverAdded) {
            myWasEverAdded = true;
            attractOnStartup();
            myInitialized.setDone();
          }
        }));
      }
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (!ScreenUtil.isStandardAddRemoveNotify(this)) {
        return;
      }

      if (Disposer.isDisposed(RunnerContentUi.this)) return;

      if (myWasEverAdded) {
        saveUiState();
      }
    }
  }

  @SuppressWarnings("SSBasedInspection")
  // [kirillk] this is done later since "startup" attractions should be done gently, only if no explicit calls are done
  private void attractOnStartup() {
    final int currentCount = myAttractionCount;
    SwingUtilities.invokeLater(() -> {
      if (currentCount < myAttractionCount) return;
      attractByCondition(LayoutViewOptions.STARTUP, false);
    });
  }

  public void attract(final Content content, boolean afterInitialized) {
    processAttraction(content.getUserData(ViewImpl.ID), null, new LayoutAttractionPolicy.Bounce(),
                      afterInitialized, true);
  }

  void attractByCondition(@NotNull String condition, boolean afterInitialized) {
    processAttractionByCondition(condition, afterInitialized, true);
  }

  void clearAttractionByCondition(String condition, boolean afterInitialized) {
    processAttractionByCondition(condition, afterInitialized, false);
  }

  private void processAttractionByCondition(@NotNull String condition, boolean afterInitialized, boolean activate) {
    processAttraction(myLayoutSettings.getToFocus(condition), condition, myLayoutSettings.getAttractionPolicy(condition),
                      afterInitialized, activate);
  }

  private void processAttraction(@Nullable String contentId, @Nullable String condition,
                                 @NotNull LayoutAttractionPolicy defaultPolicy,
                                 boolean afterInitialized,
                                 boolean activate) {
    if (contentId == null) return;
    IdeFocusManager.getInstance(getProject()).doWhenFocusSettlesDown(() -> myInitialized.processOnDone(() -> {
      Content content = findContent(contentId);
      if (content == null) return;

      LayoutAttractionPolicy policy = getOrCreatePolicyFor(contentId, condition, myAttractions, defaultPolicy);
      if (activate) {
        // See IDEA-93683, bounce attraction should not disable further focus attraction
        if (!(policy instanceof LayoutAttractionPolicy.Bounce)) {
          myAttractionCount++;
        }
        policy.attract(content, myRunnerUi);
      }
      else {
        policy.clearAttraction(content, myRunnerUi);
      }
    }, afterInitialized));
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

  ContentUI getContentUI() {
    return this;
  }

  @Override
  public void minimize(final Content content, final CellTransform.Restore restore) {
    getStateFor(content).setMinimizedInGrid(true);
    myManager.removeContent(content, false);
    saveUiState();
  }

  public void restore(@NotNull Content content) {
    GridImpl grid = getGridFor(content, false);
    if (grid == null) {
      getStateFor(content).assignTab(myLayoutSettings.getOrCreateTab(-1));
    }
    else {
      grid.getCellFor(content).restore(content);
    }
    getStateFor(content).setMinimizedInGrid(false);
    myManager.addContent(content);
    saveUiState();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public CellTransform.Facade getCellTransform() {
    return this;
  }

  @Override
  public ContentManager getContentManager() {
    return myManager;
  }

  @Override
  public @NotNull ActionManager getActionManager() {
    return myActionManager;
  }

  @Override
  public RunnerLayout getLayoutSettings() {
    return myLayoutSettings;
  }

  @Override
  public View getStateFor(final @NotNull Content content) {
    return myLayoutSettings.getStateFor(content);
  }

  @Override
  public ActionCallback select(final @NotNull Content content, final boolean requestFocus) {
    final GridImpl grid = (GridImpl)findGridFor(content);
    if (grid == null) return ActionCallback.DONE;


    final TabInfo info = myTabs.findInfo(grid);
    if (info == null) return ActionCallback.DONE;


    final ActionCallback result = new ActionCallback();
    myTabs.select(info, false).doWhenDone(() -> grid.select(content, requestFocus).notifyWhenDone(result));


    return result;
  }

  @Override
  public void validate(Content content, final ActiveRunnable toRestore) {
    final TabInfo current = myTabs.getSelectedInfo();
    myTabs.getPresentation().setPaintBlocked(true, true);

    select(content, false).doWhenDone(() -> {
      myTabs.getComponent().validate();
      toRestore.run().doWhenDone(() -> {
        assert current != null;
        myTabs.select(current, true);
        myTabs.getPresentation().setPaintBlocked(false, true);
      });
    });
  }

  @Override
  public IdeFocusManager getFocusManager() {
    return myFocusManager;
  }

  @Override
  public RunnerLayoutUi getRunnerLayoutUi() {
    return myRunnerUi;
  }

  @Override
  public @NotNull String getName() {
    return mySessionName;
  }

  @Override
  public @NotNull List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<>();
    if (myLeftToolbarActions != null) {
      AnAction[] kids = myLeftToolbarActions.getChildren(null);
      ContainerUtil.addAll(result, kids);
    }
    if (myTopLeftActions != null && Registry.is("debugger.new.tool.window.layout")) {
      AnAction[] kids = myTopLeftActions.getChildren(null);
      ContainerUtil.addAll(result, kids);
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
    return myChildren.stream().anyMatch(child -> child.getWindow() == i);
  }

  private DockManagerImpl getDockManager() {
    return (DockManagerImpl)DockManager.getInstance(myProject);
  }

  class MyDragOutDelegate implements TabInfo.DragOutDelegate {
    private DragSession mySession;

    @Override
    public void dragOutStarted(@NotNull MouseEvent mouseEvent, @NotNull TabInfo info) {
      JComponent component = info.getComponent();
      Content[] data = CONTENT_KEY.getData((DataProvider)component);
      assert data != null;
      storeDefaultIndices(data);

      final Dimension size = info.getComponent().getSize();
      final Image image = JBTabsImpl.getComponentImage(info);
      if (component instanceof Grid) {
        info.setHidden(true);
      }

      Presentation presentation = new Presentation(info.getText());
      presentation.setIcon(info.getIcon());
      mySession = getDockManager().createDragSession(mouseEvent, new DockableGrid(image, presentation,
                                                                                  size,
                                                                                  Arrays.asList(data), 0));
    }

    @Override
    public void processDragOut(@NotNull MouseEvent event, @NotNull TabInfo source) {
      mySession.process(event);
    }

    @Override
    public void dragOutFinished(@NotNull MouseEvent event, TabInfo source) {
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
    private final Image myImg;
    private final Presentation myPresentation;
    private final Dimension myPreferredSize;
    private final List<Content> myContents;
    private final int myWindow;

    DockableGrid(Image img, Presentation presentation, final Dimension size, List<Content> contents, int window) {
      myImg = img;
      myPresentation = presentation;
      myPreferredSize = size;
      myContents = contents;
      myWindow = window;
    }

    @Override
    public @NotNull List<Content> getKey() {
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

    RunnerContentUi getRunnerUi() {
      return RunnerContentUi.this;
    }

    RunnerContentUi getOriginalRunnerUi() {
      return myOriginal != null ? myOriginal : RunnerContentUi.this;
    }

    public @NotNull List<Content> getContents() {
      return myContents;
    }

    @Override
    public void close() {
    }

    public int getWindow() {
      return myWindow;
    }
  }

  public static class ShowDebugContentAction extends AnAction implements DumbAware {
    public static final String ACTION_ID = "ShowDebugContent";

    private RunnerContentUi myContentUi;

    @SuppressWarnings({"UnusedDeclaration"})
    public ShowDebugContentAction() {
    }

    public ShowDebugContentAction(RunnerContentUi runner, JComponent component, @NotNull Disposable parentDisposable) {
      myContentUi = runner;
      AnAction original = ActionManager.getInstance().getAction(ShowContentAction.ACTION_ID);
      new ShadowAction(this, original, component, parentDisposable);
      ActionUtil.copyFrom(this, ShowContentAction.ACTION_ID);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myContentUi != null && myContentUi.getPopupContents().size() > 1);
      e.getPresentation().setText(ExecutionBundle.messagePointer("action.presentation.RunnerContentUi.text.show.list.of.tabs"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myContentUi.toggleContentPopup(e.getData(NAVIGATION_ACTIONS_KEY));
    }
  }

  private void fireContentOpened(@NotNull Content content) {
    for (Listener each : myDockingListeners) {
      each.contentAdded(content);
    }
  }

  private void fireContentClosed(Content content) {
    for (Listener each : myDockingListeners) {
      each.contentRemoved(content);
    }
  }

  private static final class TopToolbarContextActions {
    public final AnAction[] left;
    public final AnAction[] middle;
    public final AnAction[] right;

    private TopToolbarContextActions(AnAction[] left, AnAction[] middle, AnAction[] right) {
      this.left = left;
      this.middle = middle;
      this.right = right;
    }
  }

  private static final class TopToolbarWrappers {
    public final Wrapper left;
    public final Wrapper middle;
    public final Wrapper right;

    private TopToolbarWrappers(Wrapper left, Wrapper middle, Wrapper right) {
      this.left = left;
      this.middle = middle;
      this.right = right;
    }
  }
}
