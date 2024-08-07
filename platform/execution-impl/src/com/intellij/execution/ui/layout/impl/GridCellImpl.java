// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.*;
import com.intellij.execution.ui.layout.actions.CloseViewAction;
import com.intellij.execution.ui.layout.actions.MinimizeViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.MutualMap;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class GridCellImpl implements GridCell {
  private final GridImpl myContainer;

  private final Set<Content> myMinimizedContents = new HashSet<>();

  private final GridCellTabs myTabs;
  private final GridImpl.Placeholder myPlaceholder;
  private final PlaceInGrid myPlaceInGrid;

  private final ViewContextEx myContext;
  private JBPopup myPopup;

  GridCellImpl(ViewContextEx context, @NotNull GridImpl container, GridImpl.Placeholder placeholder, PlaceInGrid placeInGrid) {
    myContext = context;
    myContainer = container;

    myPlaceInGrid = placeInGrid;
    myPlaceholder = placeholder;
    myPlaceholder.setContentProvider(() -> getContents());
    myTabs = new GridCellTabs(context, container);

    myTabs.getPresentation().setSideComponentVertical(true)
      .setFocusCycle(false).setPaintFocus(true)
      .setTabDraggingEnabled(context.isMoveToGridActionEnabled()).setSideComponentOnTabs(false);

    myTabs.addTabMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          // see RunnerContentUi tabMouseListener as well
          closeOrMinimize(e);
        }
      }
    });
    rebuildPopupGroup();
    myTabs.addListener(new TabsListener() {

      @Override
      public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        if (oldSelection != null && myContext.isStateBeingRestored()) {
          saveUiState();
        }
      }

      @Override
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        updateSelection(myTabs.getComponent().isShowing());

        if (!myTabs.getComponent().isShowing()) return;

        if (newSelection != null) {
          newSelection.stopAlerting();
        }
      }
    });
  }

  public void rebuildPopupGroup() {
    myTabs.setPopupGroup(myContext.getCellPopupGroup(ViewContext.CELL_POPUP_PLACE),
                         ViewContext.CELL_POPUP_PLACE, true);
  }

  public PlaceInGrid getPlaceInGrid() {
    return myPlaceInGrid;
  }

  void add(final Content content) {
    if (myTabs.myContents.containsKey(content)) return;
    myTabs.myContents.put(content, null);

    revalidateCell(() -> myTabs.addTab(createTabInfoFor(content)));

    updateSelection(myTabs.getComponent().getRootPane() != null);
  }

  void remove(Content content) {
    if (!myTabs.myContents.containsKey(content)) return;

    final TabInfo info = getTabFor(content);
    myTabs.myContents.remove(content);

    revalidateCell(() -> myTabs.removeTab(info));

    updateSelection(myTabs.getComponent().getRootPane() != null);
  }

  private void revalidateCell(Runnable contentAction) {
    if (myTabs.myContents.size() == 0) {
      myPlaceholder.removeAll();
      myTabs.removeAllTabs();

      if (myPopup != null) {
        myPopup.cancel();
        myPopup = null;
      }
    }
    else {
      if (myPlaceholder.isNull()) {
        myPlaceholder.setContent(myTabs.getComponent());
      }

      contentAction.run();
    }

    restoreProportions();

    myTabs.getComponent().revalidate();
    myTabs.getComponent().repaint();
  }

  void setHideTabs(boolean hide) {
    myTabs.getPresentation().setHideTabs(hide);
  }

  private TabInfo createTabInfoFor(Content content) {
    final TabInfo tabInfo = updatePresentation(new TabInfo(new ProviderWrapper(content, myContext)), content)
      .setObject(content)
      .setPreferredFocusableComponent(content.getPreferredFocusableComponent())
      .setActionsContextComponent(content.getActionsContextComponent());

    myTabs.myContents.remove(content);
    myTabs.myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myContext.getActionManager().getAction(RunnerContentUi.VIEW_TOOLBAR);
    tabInfo.setTabLabelActions(group, ViewContext.CELL_TOOLBAR_PLACE);
    tabInfo.setDragOutDelegate(((RunnerContentUi)myContext).myDragOutDelegate);
    return tabInfo;
  }

  private static @Nullable TabInfo updatePresentation(TabInfo info, Content content) {
    if (info == null) {
      return null;
    }

    return info.
      setIcon(content.getIcon()).
      setText(content.getDisplayName()).
      setTooltipText(content.getDescription()).
      setActionsContextComponent(content.getActionsContextComponent()).
      setActions(content.getActions(), content.getPlace()).
      setTabColor(content.getTabColor());
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    final TabInfo tabInfo = myTabs.myContents.getValue(content);
    return tabInfo != null ? myTabs.select(tabInfo, requestFocus) : ActionCallback.DONE;
  }

  public void processAlert(final Content content, final boolean activate) {
    if (myMinimizedContents.contains(content)) {
      content.fireAlert();
    }

    TabInfo tab = getTabFor(content);
    if (tab == null) return;
    if (myTabs.getSelectedInfo() != tab) {
      if (activate) {
        tab.fireAlert();
      }
      else {
        tab.stopAlerting();
      }
    }
  }

  public void updateTabPresentation(Content content) {
    updatePresentation(myTabs.findInfo(content), content);
  }

  public boolean isMinimized(Content content) {
    return myMinimizedContents.contains(content);
  }

  public boolean contains(Component c) {
    return myTabs.getComponent().isAncestorOf(c);
  }

  private static final class ProviderWrapper extends NonOpaquePanel implements UiDataProvider {
    Content myContent;
    ViewContext myContext;

    private ProviderWrapper(final Content content, final ViewContext context) {
      myContent = content;
      myContext = context;
      setLayout(new BorderLayout());
      add(content.getComponent(), BorderLayout.CENTER);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(ViewContext.CONTENT_KEY, new Content[]{myContent});
      sink.set(ViewContext.CONTEXT_KEY, myContext);
    }
  }

  @Nullable
  TabInfo getTabFor(Content content) {
    return myTabs.myContents.getValue(content);
  }

  private @NotNull Content getContentFor(TabInfo tab) {
    return myTabs.myContents.getKey(tab);
  }

  public void setToolbarHorizontal(final boolean horizontal) {
    myTabs.getPresentation().setSideComponentVertical(!horizontal);
  }

  public void setToolbarBefore(final boolean before) {
    myTabs.getPresentation().setSideComponentBefore(before);
  }

  public ActionCallback restoreLastUiState() {
    final ActionCallback result = new ActionCallback();

    restoreProportions();

    final Content[] contents = getContents();
    final List<Content> toMinimize = new SmartList<>();

    int window = 0;
    for (final Content each : contents) {
      final View view = myContainer.getStateFor(each);
      if (view.isMinimizedInGrid()) {
        toMinimize.add(each);
      }

      window = view.getWindow();
    }

    minimize(toMinimize.toArray(new Content[0]));

    final Tab tab = myContainer.getTab();
    final boolean detached = (tab != null && tab.isDetached(myPlaceInGrid)) || window != myContext.getWindow();
    if (detached && contents.length > 0) {
      if (tab != null) {
        tab.setDetached(myPlaceInGrid, false);
      }
      myContext.detachTo(window, this).notifyWhenDone(result);
    }
    else {
      result.setDone();
    }

    return result;
  }

  Content[] getContents() {
    return myTabs.myContents.getKeys().toArray(new Content[myTabs.myContents.size()]);
  }

  @Override
  public int getContentCount() {
    return myTabs.myContents.size();
  }

  public void saveUiState() {
    saveProportions();

    for (Content each : myTabs.myContents.getKeys()) {
      saveState(each, false);
    }

    for (Content each : myMinimizedContents) {
      saveState(each, true);
    }

    final DimensionService service = DimensionService.getInstance();
    final Dimension size = myContext.getContentManager().getComponent().getSize();
    service.setSize(getDimensionKey(), size, myContext.getProject());
    if (myContext.getWindow() != 0) {
      final Window frame = SwingUtilities.getWindowAncestor(myPlaceholder);
      if (frame != null && frame.isShowing()) {
        service.setLocation(getDimensionKey(), frame.getLocationOnScreen());
      }
    }
  }

  public void saveProportions() {
    myContainer.saveSplitterProportions(myPlaceInGrid);
  }

  private void saveState(Content content, boolean minimized) {
    View state = myContext.getStateFor(content);
    state.setMinimizedInGrid(minimized);
    state.setPlaceInGrid(myPlaceInGrid);
    final List<Content> contents = myContainer.getContents();
    final Tab tab = myContainer.getTabIndex();
    if (minimized && contents.size() == 1 && contents.get(0).equals(content)) {
      state.setTabIndex(-1);
      if (tab instanceof TabImpl) {
        ((TabImpl)tab).setIndex(-1);
      }
    }
    state.assignTab(tab);
    state.setWindow(myContext.getWindow());
  }

  public void restoreProportions() {
    myContainer.restoreLastSplitterProportions(myPlaceInGrid);
  }

  public void updateSelection(final boolean isShowing) {
    ContentManager contentManager = myContext.getContentManager();
    if (contentManager.isDisposed()) return;

    for (Content each : myTabs.myContents.getKeys()) {
      final TabInfo eachTab = getTabFor(each);
      boolean isSelected = eachTab != null && myTabs.getSelectedInfo() == eachTab;
      if (isSelected && isShowing) {
        contentManager.addSelectedContent(each);
      }
      else {
        contentManager.removeFromSelection(each);
      }
    }

    for (Content each : myMinimizedContents) {
      contentManager.removeFromSelection(each);
    }
  }

  public void minimize(Content[] contents) {
    if (contents.length == 0) return;
    myContext.saveUiState();

    for (final Content each : contents) {
      myMinimizedContents.add(each);
      remove(each);
      saveState(each, true);
      boolean isShowing = myTabs.getComponent().getRootPane() != null;
      myContainer.minimize(each, new CellTransform.Restore() {
        @Override
        public ActionCallback restoreInGrid() {
          restore(each);
          return ActionCallback.DONE;
        }
      });
      updateSelection(isShowing);
    }
  }

  public @Nullable Point getLocation() {
    return DimensionService.getInstance().getLocation(getDimensionKey(), myContext.getProject());
  }

  public @Nullable Dimension getSize() {
    return DimensionService.getInstance().getSize(getDimensionKey(), myContext.getProject());
  }

  private String getDimensionKey() {
    return "GridCell.Tab." + myContainer.getTab().getIndex() + "." + myPlaceInGrid.name();
  }

  public boolean isValidForCalculateProportions() {
    return getContentCount() > 0;
  }

  @Override
  public void minimize(Content content) {
    minimize(new Content[]{content});
  }

  public void closeOrMinimize(MouseEvent e) {
    TabInfo tabInfo = myTabs.findInfo(e);
    if (tabInfo == null) return;

    Content content = getContentFor(tabInfo);
    if (CloseViewAction.isEnabled(new Content[]{content})) {
      CloseViewAction.perform(myContext, content);
    }
    else if (MinimizeViewAction.isEnabled(myContext, getContents(), ViewContext.CELL_TOOLBAR_PLACE)) {
      minimize(content);
    }
  }

  void restore(@NotNull Content content) {
    myMinimizedContents.remove(content);
  }

  private static final class GridCellTabs extends SingleHeightTabs {
    final ViewContextEx myContext;
    final MutualMap<Content, TabInfo> myContents = new MutualMap<>(true);

    @Override
    protected @NotNull TabPainterAdapter createTabPainterAdapter() {
      return new DefaultTabPainterAdapter(JBTabPainter.getDEBUGGER());
    }

    private GridCellTabs(@NotNull ViewContextEx context, @NotNull GridImpl container) {
      super(context.getProject(), context.getFocusManager(), container);

      myContext = context;
      JBRunnerTabsBase tabs = ((RunnerContentUi)myContext).tabs;
      ((JBTabsImpl)tabs).addNestedTabs(this, container);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      TabInfo target = getTargetInfo();
      Content content = target == null ? null : myContents.getKey(target);
      sink.set(ViewContext.CONTENT_KEY, content == null ? null : new Content[]{content});
      sink.set(ViewContext.CONTEXT_KEY, myContext);
    }

    @Override
    protected @NotNull DragHelper createDragHelper(@NotNull JBTabsImpl tabs,
                                                   @NotNull Disposable parentDisposable) {
      return new DragHelper(tabs, parentDisposable) {
        @Override
        protected boolean canFinishDragging(@NotNull MouseEvent me) {
          return true;
        }
      };
    }

    @Override
    public boolean useSmallLabels() {
      return true;
    }

    @Override
    public void processDropOver(@NotNull TabInfo over, @NotNull RelativePoint point) {
      ((RunnerContentUi)myContext).tabs.processDropOver(over, point);
    }

    @Override
    public @NotNull Image startDropOver(@NotNull TabInfo tabInfo, @NotNull RelativePoint point) {
      return ((RunnerContentUi)myContext).tabs.startDropOver(tabInfo, point);
    }

    @Override
    public void resetDropOver(@NotNull TabInfo tabInfo) {
      ((RunnerContentUi)myContext).tabs.resetDropOver(tabInfo);
    }

    @Override
    protected @NotNull TabLabel createTabLabel(@NotNull TabInfo info) {
      return new SingleHeightLabel(this, info) {
        @Override
        public void setAlignmentToCenter(boolean toCenter) {
          super.setAlignmentToCenter(false);
        }
      };
    }
  }
}
