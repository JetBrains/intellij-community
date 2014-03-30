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

import com.intellij.execution.ui.layout.*;
import com.intellij.execution.ui.layout.actions.CloseViewAction;
import com.intellij.execution.ui.layout.actions.MinimizeViewAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.MutualMap;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GridCellImpl implements GridCell {
  private final GridImpl myContainer;

  private final MutualMap<Content, TabInfo> myContents = new MutualMap<Content, TabInfo>(true);
  private final Set<Content> myMinimizedContents = new HashSet<Content>();

  private final JBTabs myTabs;
  private final GridImpl.Placeholder myPlaceholder;
  private final PlaceInGrid myPlaceInGrid;

  private final ViewContextEx myContext;
  private JBPopup myPopup;

  public GridCellImpl(ViewContextEx context, @NotNull GridImpl container, GridImpl.Placeholder placeholder, PlaceInGrid placeInGrid) {
    myContext = context;
    myContainer = container;

    myPlaceInGrid = placeInGrid;
    myPlaceholder = placeholder;
    myTabs = new JBTabsImpl(myContext.getProject(), myContext.getActionManager(), myContext.getFocusManager(), container) {
      @Override
      protected Color getFocusedTopFillColor() {
        return  UIUtil.isUnderDarcula() ? ColorUtil.toAlpha(new Color(0x1E2533), 100)  : new Color(202, 211, 227);
      }

      @Override
      public boolean useSmallLabels() {
        return true;
      }

      @Override
      protected void paintBorder(Graphics2D g2d, ShapeInfo shape, Color borderColor) {
        if (UIUtil.isUnderDarcula()) {
          return;
        }
        super.paintBorder(g2d, shape, borderColor);
      }

      @Override
      protected Color getFocusedBottomFillColor() {
        return UIUtil.isUnderDarcula() ? new Color(0x1E2533)  : new Color(0xc2cbdb);
      }

      @Override
      public Color getBackground() {
        return UIUtil.isUnderDarcula() ? new Color(0x27292A) : super.getBackground();
      }

      @Override
      public void processDropOver(TabInfo over, RelativePoint point) {
        ((RunnerContentUi)myContext).myTabs.processDropOver(over, point);
      }

      @Override
      public Image startDropOver(TabInfo tabInfo, RelativePoint point) {
        return ((RunnerContentUi)myContext).myTabs.startDropOver(tabInfo, point);
      }

      @Override
      public void resetDropOver(TabInfo tabInfo) {
        ((RunnerContentUi)myContext).myTabs.resetDropOver(tabInfo);
      }
    }.setDataProvider(new DataProvider() {
      @Override
      @Nullable
      public Object getData(@NonNls final String dataId) {
        if (ViewContext.CONTENT_KEY.is(dataId)) {
          TabInfo target = myTabs.getTargetInfo();
          if (target != null) {
            return new Content[]{getContentFor(target)};
          }
        }
        else if (ViewContext.CONTEXT_KEY.is(dataId)) {
          return myContext;
        }

        return null;
      }
    });
    myTabs.getPresentation().setUiDecorator(new UiDecorator() {
      @Override
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(1, -1, 1, -1));
      }
    }).setSideComponentVertical(!context.getLayoutSettings().isToolbarHorizontal())
      .setStealthTabMode(true).setFocusCycle(false).setPaintFocus(true)
      .setProvideSwitchTargets(false).setTabDraggingEnabled(true).setSideComponentOnTabs(false);

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
    myTabs.addListener(new TabsListener.Adapter() {

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
    if (myContents.containsKey(content)) return;
    myContents.put(content, null);

    revalidateCell(new Runnable() {
      @Override
      public void run() {
        myTabs.addTab(createTabInfoFor(content));
      }
    });

    updateSelection(myTabs.getComponent().getRootPane() != null);
  }

  void remove(Content content) {
    if (!myContents.containsKey(content)) return;

    final TabInfo info = getTabFor(content);
    myContents.remove(content);

    revalidateCell(new Runnable() {
      @Override
      public void run() {
        myTabs.removeTab(info);
      }
    });

    updateSelection(myTabs.getComponent().getRootPane() != null);
  }

  private void revalidateCell(Runnable contentAction) {
    if (myContents.size() == 0) {
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
    final JComponent c = content.getComponent();

    final TabInfo tabInfo = updatePresentation(new TabInfo(new ProviderWrapper(content, myContext)), content)
      .setObject(content)
      .setPreferredFocusableComponent(content.getPreferredFocusableComponent())
      .setActionsContextComponent(content.getActionsContextComponent());

    myContents.remove(content);
    myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myContext.getActionManager().getAction(RunnerContentUi.VIEW_TOOLBAR);
    tabInfo.setTabLabelActions(group, ViewContext.CELL_TOOLBAR_PLACE);
    tabInfo.setDragOutDelegate(((RunnerContentUi)myContext).myDragOutDelegate);
    return tabInfo;
  }

  @Nullable
  private static TabInfo updatePresentation(TabInfo info, Content content) {
    if (info == null) return info;
    return info.
      setIcon(content.getIcon()).
      setText(content.getDisplayName()).
      setTooltipText(content.getDescription()).
      setActionsContextComponent(content.getActionsContextComponent()).
      setActions(content.getActions(), content.getPlace());
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    final TabInfo tabInfo = myContents.getValue(content);
    return tabInfo != null ? myTabs.select(tabInfo, requestFocus) : new ActionCallback.Done();
  }

  public void processAlert(final Content content, final boolean activate) {
    if (myMinimizedContents.contains(content)) return;

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

  public List<SwitchTarget> getTargets(boolean onlyVisible) {
    if (myTabs.getPresentation().isHideTabs()) return new ArrayList<SwitchTarget>();

    return myTabs.getTargets(onlyVisible, false);
  }

  public SwitchTarget getTargetForSelection() {
    return myTabs.getCurrentTarget();
  }

  public boolean contains(Component c) {
    return myTabs.getComponent().isAncestorOf(c);
  }

  private static class ProviderWrapper extends NonOpaquePanel implements DataProvider {

    Content myContent;
    ViewContext myContext;

    private ProviderWrapper(final Content content, final ViewContext context) {
      myContent = content;
      myContext = context;
      setLayout(new BorderLayout());
      add(content.getComponent(), BorderLayout.CENTER);
    }

    @Override
    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (ViewContext.CONTENT_KEY.is(dataId)) {
        return new Content[]{myContent};
      }
      else if (ViewContext.CONTEXT_KEY.is(dataId)) {
        return myContext;
      }
      return null;
    }
  }

  @Nullable
  TabInfo getTabFor(Content content) {
    return myContents.getValue(content);
  }

  @NotNull
  private Content getContentFor(TabInfo tab) {
    return myContents.getKey(tab);
  }

  public void setToolbarHorizontal(final boolean horizontal) {
    myTabs.getPresentation().setSideComponentVertical(!horizontal);
  }

  public ActionCallback restoreLastUiState() {
    final ActionCallback result = new ActionCallback();

    restoreProportions();

    Content[] contents = getContents();
    int window = 0;
    for (Content each : contents) {
      final View view = myContainer.getStateFor(each);
      if (view.isMinimizedInGrid()) {
        minimize(each);
      }
      window = view.getWindow();
    }
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
    return myContents.getKeys().toArray(new Content[myContents.size()]);
  }

  @Override
  public int getContentCount() {
    return myContents.size();
  }

  public void saveUiState() {
    saveProportions();

    for (Content each : myContents.getKeys()) {
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
      if (frame != null) {
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
    state.assignTab(myContainer.getTabIndex());
    state.setWindow(myContext.getWindow());
  }

  public void restoreProportions() {
    myContainer.restoreLastSplitterProportions(myPlaceInGrid);
  }

  public void updateSelection(final boolean isShowing) {
    ContentManager contentManager = myContext.getContentManager();
    if (contentManager.isDisposed()) return;

    for (Content each : myContents.getKeys()) {
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
    myContext.saveUiState();

    for (final Content each : contents) {
      myMinimizedContents.add(each);
      remove(each);
      boolean isShowing = myTabs.getComponent().getRootPane() != null;
      updateSelection(isShowing);
      myContainer.minimize(each, new CellTransform.Restore() {
        @Override
        public ActionCallback restoreInGrid() {
          return restore(each);
        }
      });
    }
  }

  @Nullable
  public Point getLocation() {
    return DimensionService.getInstance().getLocation(getDimensionKey(), myContext.getProject());
  }

  @Nullable
  public Dimension getSize() {
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

  ActionCallback restore(Content content) {
    myMinimizedContents.remove(content);
    return new ActionCallback.Done();
  }
}
