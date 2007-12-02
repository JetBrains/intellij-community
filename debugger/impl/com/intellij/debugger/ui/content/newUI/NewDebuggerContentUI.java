package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.DebuggerLayoutSettings;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.DebuggerContentUI;
import com.intellij.debugger.ui.content.DebuggerContentUIFacade;
import com.intellij.debugger.ui.content.newUI.actions.RestoreViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.AwtVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

public class NewDebuggerContentUI
  implements ContentUI, DebuggerContentInfo, Disposable, DebuggerContentUIFacade, DebuggerActions, CellTransform.Facade, ViewContext {

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

  Map<Grid, DefaultActionGroup> myMinimizedViewActions = new HashMap<Grid, DefaultActionGroup>();
  Map<Grid, Wrapper> myToolbarPlaceholders = new HashMap<Grid, Wrapper>();

  boolean myUiLastStateWasRestored;
  private boolean myStateIsBeingRestored;

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
    myTabs.setPopupGroup((ActionGroup)myActionManager.getAction(DebuggerActions.DEBUGGER_VIEW), TAB_POPUP_PLACE);
    myTabs.setPaintBorder(false);
    myTabs.setPaintFocus(false);
    myTabs.setRequestFocusOnLastFocusedComponent(true);

    myComponent.setContent(myTabs);

    myDebuggerActions.add(myActionManager.getAction(SHOW_EXECUTION_POINT));
    myDebuggerActions.addSeparator();
    myDebuggerActions.add(myActionManager.getAction(STEP_OVER));
    myDebuggerActions.add(myActionManager.getAction(STEP_INTO));
    myDebuggerActions.add(myActionManager.getAction(FORCE_STEP_INTO));
    myDebuggerActions.add(myActionManager.getAction(STEP_OUT));
    myDebuggerActions.addSeparator();
    myDebuggerActions.add(myActionManager.getAction(RUN_TO_CURSOR));
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

        updateTabsUI();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        Grid grid = findGridFor(event.getContent());
        if (grid != null) {
          grid.remove(event.getContent());
          removeGridIfNeeded(grid);
        }
        updateTabsUI();
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
      }
    });
  }

  private void removeGridIfNeeded(Grid grid) {
    if (grid.isEmpty()) {
      myTabs.removeTab(grid);
      myToolbarPlaceholders.remove(grid);
      myMinimizedViewActions.remove(grid);
      Disposer.dispose(grid);
    }
  }

  private Grid getGridFor(Content content, boolean createIfMissing) {
    Grid grid = findGridFor(content);
    if (grid != null || !createIfMissing) return grid;

    grid = new Grid(this, mySessionName);
    grid.setBorder(new EmptyBorder(1, 0, 0, 0));

    TabInfo tab = new TabInfo(grid).setObject(getStateFor(content).getTab()).setText("Tab");

    NonOpaquePanel toolbar = new NonOpaquePanel(new BorderLayout());
    toolbar
      .add(myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, myDebuggerActions, true).getComponent(), BorderLayout.WEST);

    NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());

    Wrapper placeholder = new Wrapper();
    myToolbarPlaceholders.put(grid, placeholder);
    wrapper.add(placeholder, BorderLayout.EAST);
    toolbar.add(wrapper, BorderLayout.CENTER);


    if (!myMinimizedViewActions.containsKey(grid)) {
      myMinimizedViewActions.put(grid, new DefaultActionGroup());
    }

    tab.setSideComponent(toolbar);

    tab.setTabLabelActions((ActionGroup)myActionManager.getAction(DebuggerActions.DEBUGGER_VIEW), TAB_TOOLBAR_PLACE);

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

  private void rebuildMinimizedActions() {
    for (Grid eachGrid : myMinimizedViewActions.keySet()) {
      DefaultActionGroup eachGroup = myMinimizedViewActions.get(eachGrid);
      Wrapper eachPlaceholder = myToolbarPlaceholders.get(eachGrid);

      JComponent minimized = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, eachGroup, true).getComponent();
      eachPlaceholder.setContent(minimized);
    }

    myTabs.revalidate();
    myTabs.repaint();
  }

  private void updateTabsUI() {
    java.util.List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      updateTabUI(each);
    }
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


    tab.setText(title).setIcon(icon);
  }

  private void restoreLastUiState() {
    if (myStateIsBeingRestored) return;

    try {
      myStateIsBeingRestored = true;

      if (!NewDebuggerContentUI.ensureValid(myTabs)) return;


      List<TabInfo> tabs = myTabs.getTabs();
      for (TabInfo each : tabs) {
        getGridFor(each).restoreLastUiState();
      }

      restoreLastSelectedTab();
    }
    finally {
      myStateIsBeingRestored = false;
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
    if (myStateIsBeingRestored) return;

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
    myManager.removeAllContents(false);

    getSettings().getLayoutSettings().resetToDefault();
    for (Content each : all) {
      myManager.addContent(each);
    }
    restoreLastUiState();
  }

  public static boolean isActive() {
    return !"true".equalsIgnoreCase(System.getProperty("old.debugger.ui"));
  }


  private class MyComponent extends Wrapper.FocusHolder implements DataProvider {
    public MyComponent() {
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (DebuggerContentUI.KEY.getName().equals(dataId)) {
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

    if (eachParent == null) return false;

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
        myMinimizedViewActions.get(getGridFor(content, false)).remove(restoreAction.get());
        return restore.restoreInGrid().doWhenDone(new Runnable() {
          public void run() {
            saveUiState();
            select(content, true);
            rebuildMinimizedActions();
          }
        });
      }
    }));

    Grid grid = getGridFor(content, false);
    myMinimizedViewActions.get(grid).add(restoreAction.get());

    saveUiState();
    rebuildMinimizedActions();
  }

  public void moveToTab(final Content content) {
    myManager.removeContent(content, false);
    getStateFor(content).setTab(getLayoutSettings().createNewTab());
    getStateFor(content).setPlaceInGrid(PlaceInGrid.center);
    myManager.addContent(content);
  }

  public void moveToGrid(final Content content) {
    myManager.removeContent(content, false);
    getStateFor(content).setTab(getLayoutSettings().getDefaultTab());
    getStateFor(content).setPlaceInGrid(getLayoutSettings().getDefaultGridPlace(content));
    myManager.addContent(content);
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

  public NewContentState getStateFor(final Content content) {
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
        grid.select(content, requestFocus).markDone(result);
      }
    });

    return result;
  }
}
