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
import com.intellij.execution.ui.layout.actions.RestoreViewAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.AppIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.UiDecorator;
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

public class RunnerContentUi implements ContentUI, Disposable, CellTransform.Facade, ViewContextEx, PropertyChangeListener, SwitchProvider,
                                        QuickActionProvider {

  @NonNls public static final String LAYOUT = "Runner.Layout";
  @NonNls public static final String VIEW_POPUP = "Runner.View.Popup";
  @NonNls public static final String VIEW_TOOLBAR = "Runner.View.Toolbar";

  ContentManager myManager;
  RunnerLayout myLayoutSettings;

  ActionManager myActionManager;
  String mySessionName;
  MyComponent myComponent = new MyComponent();

  private final Wrapper myToolbar = new Wrapper();

  JBTabs myTabs;
  private final Comparator<TabInfo> myTabsComparator = new Comparator<TabInfo>() {
    public int compare(final TabInfo o1, final TabInfo o2) {
      return getTabFor(o1).getIndex() - getTabFor(o2).getIndex();
    }
  };
  Project myProject;

  ActionGroup myTopActions = new DefaultActionGroup();

  DefaultActionGroup myMinimizedViewActions = new DefaultActionGroup();

  Map<GridImpl, Wrapper> myMinimizedButtonsPlaceholder = new HashMap<GridImpl, Wrapper>();
  Map<GridImpl, Wrapper> myCommonActionsPlaceholder = new HashMap<GridImpl, Wrapper>();
  Map<GridImpl, Set<Content>> myContextActions = new HashMap<GridImpl, Set<Content>>();

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

    myTabs = new JBTabsImpl(myProject, myActionManager, myFocusManager, this)
        .setDataProvider(new DataProvider() {
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
        }).setProvideSwitchTargets(false).setInnerInsets(new Insets(1, 0, 0, 0)).setToDrawBorderIfTabsHidden(false).setUiDecorator(new UiDecorator() {
        @NotNull
        public UiDecoration getDecoration() {
          return new UiDecoration(null, new Insets(1, 8, 1, 8));
        }
      }).getJBTabs();
    rebuildTabPopup();


    myTabs.getPresentation().setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(2).setPaintFocus(false).setRequestFocusOnLastFocusedComponent(true);

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

  public void doWhenInitialized(final Runnable runnable) {
    myInitialized.doWhenDone(runnable);
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
    } else if (Content.PROP_DISPLAY_NAME.equals(property)
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
      } else {
        tab.stopAlerting();
      }
    } else {
      grid.processAlert(content, activate);
    }
  }

  public JComponent getComponent() {
    initUi();
    return myComponent;
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

        grid.add(event.getContent());

        if (getSelectedGrid() == grid) {
          grid.processAddToUi(false);
        }

        if (myManager.getComponent().isShowing()) {
          grid.restoreLastUiState();
        }

        updateTabsUI(false);


        event.getContent().addPropertyChangeListener(RunnerContentUi.this);
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

      if (!contents.equals(myContextActions.get(entry.getKey()))) {
        ActionToolbar tb = myActionManager.createActionToolbar(myActionsPlace, groupToBuild, true);
        tb.setTargetComponent(contextComponent);
        eachPlaceholder.setContent(tb.getComponent());
      }

      if (groupToBuild.getChildrenCount() > 0) {
        hasToolbarContent = true;
      }

      myContextActions.put(entry.getKey(), contents);
    }

    return hasToolbarContent;
  }

  private boolean rebuildMinimizedActions() {
    for (Map.Entry<GridImpl, Wrapper> entry : myMinimizedButtonsPlaceholder.entrySet()) {
      Wrapper eachPlaceholder = entry.getValue();
      ActionToolbar tb = myActionManager.createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, myMinimizedViewActions, true);
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

    List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      hasToolbarContent |= updateTabUI(each);
    }

    myTabs.getPresentation().setHideTabs(!hasToolbarContent);

    myTabs.updateTabActions(validateNow);

    if (validateNow) {
      myTabs.sortTabs(myTabsComparator);
    }
  }

  private static boolean updateTabUI(TabInfo tab) {
    String title = getTabFor(tab).getDisplayName();
    Icon icon = getTabFor(tab).getIcon();

    GridImpl grid = getGridFor(tab);
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

    for (TabInfo each : myTabs.getTabs()) {
      GridImpl eachGrid = getGridFor(each);
      eachGrid.saveUiState();
    }
  }

  public Tab getTabFor(final Grid grid) {
    TabInfo info = myTabs.findInfo((Component)grid);
    return getTabFor(info);
  }

  private static TabImpl getTabFor(final TabInfo tab) {
    return (TabImpl)tab.getObject();
  }

  private static GridImpl getGridFor(TabInfo tab) {
    return (GridImpl)tab.getComponent();
  }

  @Nullable
  public Grid findGridFor(Content content) {
    TabImpl tab = (TabImpl)getStateFor(content).getTab();
    for (TabInfo each : myTabs.getTabs()) {
      if (getTabFor(each).equals(tab)) return getGridFor(each);
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
    if (myComponent.getRootPane() != null) {
      saveUiState();
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
    return myMinimizeActionEnabled;
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

  private static LayoutAttractionPolicy getOrCreatePolicyFor(String key, Map<String, LayoutAttractionPolicy> map, LayoutAttractionPolicy defaultPolicy) {
    LayoutAttractionPolicy policy = map.get(key);
    if (policy == null) {
      policy = defaultPolicy;
      map.put(key, policy);
    }
    return policy;
  }

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

      if (!myUiLastStateWasRestored) {
        myUiLastStateWasRestored = true;

        //noinspection SSBasedInspection
        // [kirillk] this is done later since restoreUiState doesn't work properly in the addNotify call chain
        //todo to investigate and to fix (may cause extra flickering)
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
    processAttraction(myLayoutSettings.getToFocus(condition), myConditionAttractions, myLayoutSettings.getAttractionPolicy(condition), afterInitialized, true);
  }

  public void clearAttractionByCondition(String condition, boolean afterInitialized) {
    processAttraction(myLayoutSettings.getToFocus(condition), myConditionAttractions, new LayoutAttractionPolicy.FocusOnce(), afterInitialized, false);
  }

  private void processAttraction(final String contentId, final Map<String, LayoutAttractionPolicy> policyMap, final LayoutAttractionPolicy defaultPolicy, final boolean afterInitialized, final boolean activate) {
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

  private static boolean willBeEmptyOnRemove(GridImpl grid, List<Content> toRemove) {
    List<Content> attachedToGrid = grid.getAttachedContents();
    for (Content each : attachedToGrid) {
      if (!toRemove.contains(each)) return false;
    }

    return true;
  }


  public CellTransform.Restore detach(final Content[] content) {
    List<Content> contents = Arrays.asList(content);

    for (Content each : content) {
      GridImpl eachGrid = getGridFor(each, false);
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
      GridImpl eachGrid = (GridImpl)eachInfos.getComponent();
      if (!eachGrid.getAttachedContents().isEmpty()) {
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
    final GridImpl grid = (GridImpl)findGridFor(content);
    if (grid == null) return new ActionCallback.Done();


    final TabInfo info = myTabs.findInfo(grid);
    if (info == null) return new ActionCallback.Done();


    final ActionCallback result = new ActionCallback();
    if (grid.isDetached(content)) {
      if (requestFocus) {
        grid.select(content, requestFocus).notifyWhenDone(result);
      }
    } else {
      myTabs.select(info, false).doWhenDone(new Runnable() {
        public void run() {
          grid.select(content, requestFocus).notifyWhenDone(result);
        }
      });
    }

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
      result.addAll(Arrays.asList(kids));
    }
    return result;
  }

  public SwitchTarget getCurrentTarget() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return myTabs.getCurrentTarget();

    GridImpl grid = getSelectedGrid();
    if (grid.getContents().size() <= 1) return myTabs.getCurrentTarget();

    SwitchTarget cell = grid.getCellFor(owner);

    return cell != null ? cell : myTabs.getCurrentTarget();
  }

  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    List<SwitchTarget> result = new ArrayList<SwitchTarget>();

    result.addAll(myTabs.getTargets(true, false));
    result.addAll(getSelectedGrid().getTargets(onlyVisible));

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
}
