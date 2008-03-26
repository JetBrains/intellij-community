package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.execution.ui.layout.actions.RestoreViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class RunnerContentUi implements ContentUI, Disposable, CellTransform.Facade, ViewContext, PropertyChangeListener {

  @NonNls public static final String LAYOUT = "Runner.Layout";
  @NonNls public static final String VIEW_POPUP = "Runner.View.Popup";
  @NonNls public static final String VIEW_TOOLBAR = "Runner.View.Toolbar";

  ContentManager myManager;
  RunnerLayout myLayoutSettings;

  ActionManager myActionManager;
  String mySessionName;
  MyComponent myComponent = new MyComponent();

  private Wrapper myToolbar = new Wrapper();

  JBTabs myTabs;
  private Comparator<TabInfo> myTabsComparator = new Comparator<TabInfo>() {
    public int compare(final TabInfo o1, final TabInfo o2) {
      return getTabFor(o1).getIndex() - getTabFor(o2).getIndex();
    }
  };
  Project myProject;

  ActionGroup myTopActions = new DefaultActionGroup();

  DefaultActionGroup myMinimizedViewActions = new DefaultActionGroup();

  Map<Grid, Wrapper> myMinimizedButtonsPlaceholder = new HashMap<Grid, Wrapper>();
  Map<Grid, Wrapper> myCommonActionsPlaceholder = new HashMap<Grid, Wrapper>();
  Map<Grid, Set<Content>> myContextActions = new HashMap<Grid, Set<Content>>();

  boolean myUiLastStateWasRestored;

  private Set<Object> myRestoreStateRequestors = new HashSet<Object>();
  public static final DataKey<RunnerContentUi> KEY = DataKey.create("DebuggerContentUI");
  private String myActionsPlace = ActionPlaces.UNKNOWN;
  private IdeFocusManager myFocusManager;

  private boolean myMinimizeActionEnabled = true;
  private boolean myMoveToGridActionEnabled = true;

  public RunnerContentUi(Project project,
                         ActionManager actionManager,
                         IdeFocusManager focusManager,
                         RunnerLayout settings,
                         String sessionName) {
    myProject = project;
    myLayoutSettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;
    myFocusManager = focusManager;
  }


  public void setTopActions(@NotNull final ActionGroup topActions, @NotNull String place) {
    myTopActions = topActions;
    myActionsPlace = place;

    rebuildCommonActions();
  }

  public void setLeftToolbar(ActionGroup group, String place) {
    final JComponent tb = myActionManager.createActionToolbar(place, group, false).getComponent();
    myToolbar.setContent(tb);

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void initUi() {
    if (myTabs != null) return;

    myTabs = new JBTabsImpl(myProject, myActionManager, myFocusManager, this)
        .setDataProvider(new DataProvider() {
          public Object getData(@NonNls final String dataId) {
            if (ViewContext.CONTENT_KEY.getName().equals(dataId)) {
              TabInfo info = myTabs.getTargetInfo();
              if (info != null) {
                return getGridFor(info).getData(dataId);
              }
            }
            else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
              return RunnerContentUi.this;
            }
            return null;
          }
        }).setInnerInsets(new Insets(1, 0, 0, 0));
    final ActionGroup popup = (ActionGroup)myActionManager.getAction(VIEW_POPUP);
    myTabs.setPopupGroup(popup, TAB_POPUP_PLACE, true);
    myTabs.setPaintBorder(-1, 0, 0, 0);
    myTabs.setPaintFocus(false);
    myTabs.setRequestFocusOnLastFocusedComponent(true);

    final NonOpaquePanel wrappper = new NonOpaquePanel(new BorderLayout(0, 0));
    wrappper.add(myToolbar, BorderLayout.WEST);
    wrappper.add(myTabs.getComponent(), BorderLayout.CENTER);

    myComponent.setContent(wrappper);

    myTabs.addListener(new TabsListener() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        if (!myTabs.getComponent().isShowing()) return;

        if (newSelection != null) {
          newSelection.stopAlerting();
        }
      }
    });
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    Content content = (Content)evt.getSource();
    final Grid grid = getGridFor(content, false);
    if (grid == null) return;

    final GridCell cell = grid.findCell(content);
    if (cell == null) return;

    final String property = evt.getPropertyName();
    if (Content.PROP_ALERT.equals(property)) {
      if (grid.getContents().size() == 1) {
        TabInfo info = myTabs.findInfo(grid);
        if (myTabs.getSelectedInfo() != info) {
          info.fireAlert();
        }
      }
      else {
        grid.alert(content);
      }
    } else if (Content.PROP_DISPLAY_NAME.equals(property)
               || Content.PROP_ICON.equals(property)
               || Content.PROP_ACTIONS.equals(property)
               || Content.PROP_DESCRIPTION.equals(property)) {
      cell.updateTabPresentation(content);
      updateTabsUI(false);
    }
  }

  public JComponent getComponent() {
    initUi();
    return myComponent;
  }

  public void setManager(final ContentManager manager) {
    assert myManager == null;

    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        getGridFor(event.getContent(), true).add(event.getContent(), false);
        updateTabsUI(false);

        event.getContent().addPropertyChangeListener(RunnerContentUi.this);
      }

      public void contentRemoved(final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(RunnerContentUi.this);

        Grid grid = findGridFor(event.getContent());
        if (grid != null) {
          grid.remove(event.getContent());
          removeGridIfNeeded(grid);
        }
        updateTabsUI(false);
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        if (isStateBeingRestored()) return;

        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          Grid toSelect = findGridFor(event.getContent());
          Grid selected = getSelectedGrid();

          if (selected != null && toSelect != null && selected == toSelect) {
            select(event.getContent(), true);
          }
          else {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                select(event.getContent(), true);
              }
            });
          }
        }
      }
    });
  }

  private Grid getSelectedGrid() {
    TabInfo selection = myTabs.getSelectedInfo();
    return selection != null ? getGridFor(selection) : null;
  }

  private void removeGridIfNeeded(Grid grid) {
    if (grid.isEmpty()) {
      myTabs.removeTab(myTabs.findInfo(grid));
      myMinimizedButtonsPlaceholder.remove(grid);
      myCommonActionsPlaceholder.remove(grid);
      Disposer.dispose(grid);
    }
  }

  @Nullable
  private Grid getGridFor(Content content, boolean createIfMissing) {
    Grid grid = findGridFor(content);
    if (grid != null || !createIfMissing) return grid;

    grid = new Grid(this, mySessionName);
    grid.setBorder(new EmptyBorder(1, 0, 0, 0));

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

  @Nullable
  public GridCell findCellFor(final Content content) {
    Grid cell = getGridFor(content, false);
    assert cell != null;
    return cell.getCellFor(content);
  }

  private boolean rebuildToolbar() {
    boolean hasToolbarContent = rebuildCommonActions();
    hasToolbarContent |= rebuildMinimizedActions();
    return hasToolbarContent;
  }

  private boolean rebuildCommonActions() {
    boolean hasToolbarContent = false;
    for (Grid each : myCommonActionsPlaceholder.keySet()) {
      Wrapper eachPlaceholder = myCommonActionsPlaceholder.get(each);
      DefaultActionGroup groupToBuild;
      JComponent contextComponent = null;
      List<Content> contentList = each.getContents();

      Set<Content> contents = new HashSet<Content>();
      contents.addAll(contentList);

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

      if (!contents.equals(myContextActions.get(each))) {
        ActionToolbar tb = myActionManager.createActionToolbar(myActionsPlace, groupToBuild, true);
        tb.setTargetComponent(contextComponent);
        eachPlaceholder.setContent(tb.getComponent());
      }

      if (groupToBuild.getChildrenCount() > 0) {
        hasToolbarContent = true;
      }

      myContextActions.put(each, contents);
    }

    return hasToolbarContent;
  }

  private boolean rebuildMinimizedActions() {
    for (Grid each : myMinimizedButtonsPlaceholder.keySet()) {
      Wrapper eachPlaceholder = myMinimizedButtonsPlaceholder.get(each);
      ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, myMinimizedViewActions, true);
      ((ActionToolbar)tb).setReservePlaceAutoPopupIcon(false);
      JComponent minimized = tb.getComponent();
      eachPlaceholder.setContent(minimized);
    }

    myTabs.getComponent().revalidate();
    myTabs.getComponent().repaint();

    return myMinimizedViewActions.getChildrenCount() > 0;
  }

  private void updateTabsUI(final boolean validateNow) {
    boolean hasToolbarContent = rebuildToolbar();

    java.util.List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      hasToolbarContent |= updateTabUI(each);
    }

    myTabs.setHideTabs(!hasToolbarContent);

    myTabs.updateTabActions(validateNow);

    if (validateNow) {
      myTabs.sortTabs(myTabsComparator);
    }
  }

  private boolean updateTabUI(TabInfo tab) {
    String title = getTabFor(tab).getDisplayName();
    Icon icon = getTabFor(tab).getIcon();

    Grid grid = getGridFor(tab);
    boolean hasToolbarContent = grid.updateGridUI();

    List<Content> contents = grid.getContents();
    if (title == null) {
      title = "";
      Iterator<Content> all = contents.iterator();
      while (all.hasNext()) {
        Content each = all.next();
        title += each.getTabName();
        if (all.hasNext()) {
          title += " | ";
        }
      }
    }

    if (icon == null && contents.size() == 1) {
      icon = contents.get(0).getIcon();
    }


    tab.setText(title).setIcon(grid.getTab().isDefault() ? null : icon);

    return hasToolbarContent;
  }

  private void restoreLastUiState() {
    if (isStateBeingRestored()) return;

    try {
      setStateIsBeingRestored(true, this);

      if (!RunnerContentUi.ensureValid(myTabs.getComponent())) return;


      List<TabInfo> tabs = new ArrayList<TabInfo>();
      tabs.addAll(myTabs.getTabs());
      for (TabInfo each : tabs) {
        getGridFor(each).restoreLastUiState();
      }

      restoreLastSelectedTab();
    }
    finally {
      setStateIsBeingRestored(false, this);
    }
  }

  private void restoreLastSelectedTab() {
    int index = myLayoutSettings.getDefaultSelectedTabIndex();

    if (myTabs.getTabCount() > 0) {
      myTabs.select(myTabs.getTabAt(0), false);
    }

    for (TabInfo each : myTabs.getTabs()) {
      Tab tab = getTabFor(each);
      if (tab.getIndex() == index) {
        myTabs.select(each, true);
        break;
      }
    }
  }

  public void saveUiState() {
    if (isStateBeingRestored()) return;

    for (TabInfo each : myTabs.getTabs()) {
      Grid eachGrid = getGridFor(each);
      eachGrid.saveUiState();
    }
  }

  public Tab getTabFor(final Grid grid) {
    TabInfo info = myTabs.findInfo(grid);
    return getTabFor(info);
  }

  private Tab getTabFor(final TabInfo tab) {
    return ((Tab)tab.getObject());
  }

  private Grid getGridFor(TabInfo tab) {
    return (Grid)tab.getComponent();
  }

  @Nullable
  public Grid findGridFor(Content content) {
    Tab tab = getStateFor(content).getTab();
    for (TabInfo each : myTabs.getTabs()) {
      if (getTabFor(each).equals(tab)) return getGridFor(each);
    }

    return null;
  }

  private ArrayList<Grid> getGrids() {
    ArrayList<Grid> result = new ArrayList<Grid>();
    for (TabInfo each : myTabs.getTabs()) {
      result.add(getGridFor(each));
    }
    return result;
  }


  public void setHorizontalToolbar(final boolean state) {
    myLayoutSettings.setToolbarHorizontal(state);
    for (Grid each : getGrids()) {
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
    if (myComponent.getRootPane() != null) {
      saveUiState();
    }
  }

  public void dispose() {

  }

  public void restoreLayout() {
    Content[] all = myManager.getContents();

    setStateIsBeingRestored(true, this);
    try {
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
    restoreLastUiState();

    updateTabsUI(true);
  }

  public boolean isStateBeingRestored() {
    return myRestoreStateRequestors.size() > 0;
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
    return (ActionGroup)myActionManager.getAction(RunnerContentUi.LAYOUT);
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
    return myMinimizeActionEnabled;
  }

  public boolean isMoveToGridActionEnabled() {
    return myMoveToGridActionEnabled;
  }

  private class MyComponent extends Wrapper.FocusHolder implements DataProvider {
    public MyComponent() {
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (KEY.getName().equals(dataId)) {
        return RunnerContentUi.this;
      }
      else {
        return null;
      }
    }

    public void addNotify() {
      super.addNotify();

      if (!myUiLastStateWasRestored) {
        myUiLastStateWasRestored = true;

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            restoreLastUiState();
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
    restoreAction.set(new RestoreViewAction(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        myMinimizedViewActions.remove(restoreAction.get());
        return restore.restoreInGrid().doWhenDone(new Runnable() {
          public void run() {
            saveUiState();
            select(content, true);
            updateTabsUI(false);
          }
        });
      }
    }));

    myMinimizedViewActions.add(restoreAction.get());

    saveUiState();
    updateTabsUI(false);
  }

  private static boolean willBeEmptyOnRemove(Grid grid, List<Content> toRemove) {
    List<Content> attachedToGrid = grid.getAttachedContents();
    for (Content each : attachedToGrid) {
      if (!toRemove.contains(each)) return false;
    }

    return true;
  }


  public CellTransform.Restore detach(final Content[] content) {
    List<Content> contents = Arrays.asList(content);

    for (Content each : content) {
      Grid eachGrid = getGridFor(each, false);
      if (willBeEmptyOnRemove(eachGrid, contents)) {
        myTabs.findInfo(eachGrid).setHidden(true);
      }
    }

    updateTabsUI(true);

    return new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        showHiddenTabs();
        updateTabsUI(true);
        return new ActionCallback.Done();
      }
    };
  }

  private void showHiddenTabs() {
    List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo eachInfos : tabs) {
      Grid eachGrid = (Grid)eachInfos.getComponent();
      if (eachGrid.getAttachedContents().size() > 0) {
        eachInfos.setHidden(false);
      }
    }
  }

  public void moveToTab(final Content content) {
    saveUiState();

    setStateIsBeingRestored(true, this);
    try {
      myManager.removeContent(content, false);
      getStateFor(content).assignTab(myLayoutSettings.createNewTab());
      getStateFor(content).setPlaceInGrid(RunnerLayoutUi.PlaceInGrid.center);
      myManager.addContent(content);
    }
    finally {
      setStateIsBeingRestored(false, this);
    }

    saveUiState();
  }

  public void moveToGrid(final Content content) {
    saveUiState();

    setStateIsBeingRestored(true, this);

    try {
      myManager.removeContent(content, false);
      getStateFor(content).assignTab(myLayoutSettings.getDefaultTab());
      getStateFor(content).setPlaceInGrid(myLayoutSettings.getDefaultGridPlace(content));
      myManager.addContent(content);
    }
    finally {
      setStateIsBeingRestored(false, this);
    }

    select(content, true).doWhenDone(new Runnable() {
      public void run() {
        saveUiState();
      }
    });

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
    final Grid grid = findGridFor(content);
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

  public void validate(final Content content, final ActionCallback.Runnable toRestore) {
    final TabInfo current = myTabs.getSelectedInfo();
    myTabs.setPaintBlocked(true);

    select(content, false).doWhenDone(new Runnable() {
      public void run() {
        myTabs.getComponent().validate();
        toRestore.run().doWhenDone(new Runnable() {
          public void run() {
            myTabs.select(current, true);
            myTabs.setPaintBlocked(false);
          }
        });
      }
    });
  }

  private class TwoSideComponent extends NonOpaquePanel {
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
            leftSize.width = leftSize.width - diffToRightMin;
            myLeft.setSize(leftSize);
          }
        }

        myRight.setBounds((int)myLeft.getBounds().getMaxX(), 0, parent.getWidth() - myLeft.getWidth(), parent.getHeight());
      }

      toMakeVerticallyInCenter(myLeft, parent);
      toMakeVerticallyInCenter(myRight, parent);

    }

    private void toMakeVerticallyInCenter(JComponent comp, Container parent) {
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


}
