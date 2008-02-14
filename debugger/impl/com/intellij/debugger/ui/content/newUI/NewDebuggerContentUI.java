package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.settings.DebuggerLayoutSettings;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.DebuggerContentUIFacade;
import com.intellij.debugger.ui.content.newUI.actions.RestoreViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AwtVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class NewDebuggerContentUI
  implements ContentUI, DebuggerContentInfo, Disposable, DebuggerContentUIFacade, DebuggerActions, CellTransform.Facade, ViewContext,
             PropertyChangeListener {

  ContentManager myManager;
  DebuggerSettings mySettings;

  ActionManager myActionManager;
  String mySessionName;
  MyComponent myComponent = new MyComponent();

  JBTabs myTabs;
  private Comparator<TabInfo> myTabsComparator = new Comparator<TabInfo>() {
    public int compare(final TabInfo o1, final TabInfo o2) {
      return getTabFor(o1).getIndex() - getTabFor(o2).getIndex();
    }
  };
  Project myProject;

  DefaultActionGroup myDebuggerActions = new DefaultActionGroup();

  DefaultActionGroup myMinimizedViewActions = new DefaultActionGroup();

  Map<Grid, Wrapper> myMinimizedButtonsPlaceholder = new HashMap<Grid, Wrapper>();
  Map<Grid, Wrapper> myCommonActionsPlaceholder = new HashMap<Grid, Wrapper>();
  Map<Grid, Set<Content>> myContextActions = new HashMap<Grid, Set<Content>>();

  boolean myUiLastStateWasRestored;

  private Set<Object> myRestoreStateRequestors = new HashSet<Object>();
  public static final DataKey<NewDebuggerContentUI> KEY = DataKey.create("DebuggerContentUI");

  public NewDebuggerContentUI(Project project, ActionManager actionManager, DebuggerSettings settings, String sessionName) {
    myProject = project;
    mySettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;

    myTabs = new JBTabs(project, actionManager, this) {
      @Nullable
      public Object getData(@NonNls final String dataId) {
        if (ViewContext.CONTENT_KEY.getName().equals(dataId)) {
          TabInfo info = myTabs.getTargetInfo();
          if (info != null) {
            return getGridFor(info).getData(dataId);
          }
        } else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
          return NewDebuggerContentUI.this;
        }
        return super.getData(dataId);
      }
    };
    myTabs.setPopupGroup((ActionGroup)myActionManager.getAction(DebuggerActions.DEBUGGER_VIEW_POPUP), TAB_POPUP_PLACE);
    myTabs.setPaintBorder(false);
    myTabs.setPaintFocus(false);
    myTabs.setRequestFocusOnLastFocusedComponent(true);

    myComponent.setContent(myTabs);

    myTabs.addListener(new TabsListener() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        if (!myTabs.isShowing()) return;

        if (newSelection != null) {
          newSelection.stopAlerting();
        }
      }
    });

    myDebuggerActions.add(myActionManager.getAction(SHOW_EXECUTION_POINT));
    myDebuggerActions.addSeparator();
    myDebuggerActions.add(myActionManager.getAction(STEP_OVER));
    myDebuggerActions.add(myActionManager.getAction(STEP_INTO));
    myDebuggerActions.add(myActionManager.getAction(FORCE_STEP_INTO));
    myDebuggerActions.add(myActionManager.getAction(STEP_OUT));
    myDebuggerActions.addSeparator();
    myDebuggerActions.add(myActionManager.getAction(RUN_TO_CURSOR));
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    Content content = (Content)evt.getSource();
    if (Content.PROP_ALERT.equals(evt.getPropertyName())) {
      Grid grid = getGridFor(content, false);
      if (grid.getContents().size() == 1) {
        TabInfo info = myTabs.findInfo(grid);
        if (myTabs.getSelectedInfo() != info) {
          info.fireAlert();
        }
      } else {
        grid.alert(content);
      }
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setManager(final ContentManager manager) {
    assert myManager == null;

    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        getGridFor(event.getContent(), true).add(event.getContent(), false);
        updateTabsUI(false);

        event.getContent().addPropertyChangeListener(NewDebuggerContentUI.this);
      }

      public void contentRemoved(final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(NewDebuggerContentUI.this);

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
          } else {
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
      myTabs.removeTab(grid);
      myMinimizedButtonsPlaceholder.remove(grid);
      myCommonActionsPlaceholder.remove(grid);
      Disposer.dispose(grid);
    }
  }

  private Grid getGridFor(Content content, boolean createIfMissing) {
    Grid grid = findGridFor(content);
    if (grid != null || !createIfMissing) return grid;

    grid = new Grid(this, mySessionName);
    grid.setBorder(new EmptyBorder(1, 0, 0, 0));

    TabInfo tab = new TabInfo(grid).setObject(getStateFor(content).getTab()).setText("Tab");


    Wrapper leftWrapper = new Wrapper();
    myCommonActionsPlaceholder.put(grid, leftWrapper);

    Wrapper rightWrapper = new Wrapper();
    myMinimizedButtonsPlaceholder.put(grid, rightWrapper);

    NonOpaquePanel sideComponent = new NonOpaquePanel(new CommonToolbarLayout(leftWrapper, rightWrapper));

    tab.setSideComponent(sideComponent);

    sideComponent.add(leftWrapper);
    sideComponent.add(rightWrapper);

    tab.setTabLabelActions((ActionGroup)myActionManager.getAction(DebuggerActions.DEBUGGER_VIEW_TOOLBAR), TAB_TOOLBAR_PLACE);

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

  private void rebuildToolbar() {
    rebuildCommonActions();
    rebuildMinimizedActions();
  }

  private void rebuildCommonActions() {
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
        groupToBuild.addAll(myDebuggerActions);
      } else {
        groupToBuild = myDebuggerActions;
      }

      if (!contents.equals(myContextActions.get(each))) {
        ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, groupToBuild, true);
        tb.setTargetComponent(contextComponent);
        eachPlaceholder.setContent(tb.getComponent());
      }

      myContextActions.put(each, contents);
    }
  }

  private void rebuildMinimizedActions() {
    for (Grid each : myMinimizedButtonsPlaceholder.keySet()) {
      Wrapper eachPlaceholder = myMinimizedButtonsPlaceholder.get(each);
      ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, myMinimizedViewActions, true);
      ((ActionToolbarImpl)tb).setReservePlaceAutoPopupIcon(false);
      JComponent minimized = tb.getComponent();
      eachPlaceholder.setContent(minimized);
    }

    myTabs.revalidate();
    myTabs.repaint();
  }

  private void updateTabsUI(final boolean validateNow) {
    rebuildToolbar();

    java.util.List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      updateTabUI(each);
    }

    myTabs.updateTabActions(validateNow);
  }

  private void updateTabUI(TabInfo tab) {
    String title = getTabFor(tab).getDisplayName();
    Icon icon = getTabFor(tab).getIcon();

    Grid grid = getGridFor(tab);
    grid.updateGridUI();

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
  }

  private void restoreLastUiState() {
    if (isStateBeingRestored()) return;

    try {
      setStateIsBeingRestored(true, this);

      if (!NewDebuggerContentUI.ensureValid(myTabs)) return;


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
    int index = getSettings().getLayoutSettings().getDefaultSelectedTabIndex();

    if (myTabs.getTabCount() > 0) {
      myTabs.setSelected(myTabs.getTabAt(0), false);
    }

    for (TabInfo each : myTabs.getTabs()) {
      Tab tab = getTabFor(each);
      if (tab.getIndex() == index) {
        myTabs.setSelected(each, true);
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
    getLayoutSettings().setToolbarHorizontal(state);
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
    } finally {
      setStateIsBeingRestored(false, this);
    }

    getSettings().getLayoutSettings().resetToDefault();
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
    } else {
      myRestoreStateRequestors.remove(requestor);
    }
  }


  private class MyComponent extends Wrapper.FocusHolder implements DataProvider {
    public MyComponent() {
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (KEY.getName().equals(dataId)) {
        return NewDebuggerContentUI.this;
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

      if (Disposer.isDisposed(NewDebuggerContentUI.this)) return;

      saveUiState();
    }
  }

  public static void removeScrollBorder(final Component c) {
    new AwtVisitor(c) {
      public boolean visit(final Component component) {
        if (component instanceof JScrollPane) {
          if (!hasNonPrimitiveParents(c, component)) {
            ((JScrollPane)component).setBorder(null);
          }
        }
        return false;
      }
    };
  }

  private static boolean hasNonPrimitiveParents(Component stopParent, Component c) {
    Component eachParent = c.getParent();
    while (true) {
      if (eachParent == null || eachParent == stopParent) return false;
      if (!isPrimitive(eachParent)) return true;
      eachParent = eachParent.getParent();
    }
  }


  private static boolean isPrimitive(Component c) {
    return c instanceof JPanel;
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
      getStateFor(content).assignTab(getLayoutSettings().createNewTab());
      getStateFor(content).setPlaceInGrid(PlaceInGrid.center);
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
      getStateFor(content).assignTab(getLayoutSettings().getDefaultTab());
      getStateFor(content).setPlaceInGrid(getLayoutSettings().getDefaultGridPlace(content));
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

  private DebuggerLayoutSettings getLayoutSettings() {
    return mySettings.getLayoutSettings();
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

  public DebuggerSettings getSettings() {
    return DebuggerSettings.getInstance();
  }

  public View getStateFor(final Content content) {
    return getSettings().getLayoutSettings().getStateFor(content);
  }

  public boolean isHorizontalToolbar() {
    return getSettings().getLayoutSettings().isToolbarHorizontal();
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
          myTabs.validate();
          toRestore.run().doWhenDone(new Runnable() {
            public void run() {
              myTabs.select(current, true);
              myTabs.setPaintBlocked(false);
            }
          });
        }
      });
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
      } else {
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
    }
  }
}
