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
import com.intellij.execution.ui.layout.actions.MinimizeViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class GridCellImpl implements GridCell, Disposable {

  private final GridImpl myContainer;

  private final MutualMap<Content, TabInfo> myContents = new MutualMap<Content, TabInfo>(true);
  private final Set<Content> myMinimizedContents = new HashSet<Content>();

  private final JBTabs myTabs;
  private final GridImpl.Placeholder myPlaceholder;
  private final PlaceInGrid myPlaceInGrid;

  private final ViewContextEx myContext;
  private CellTransform.Restore.List myRestoreFromDetach;
  private JBPopup myPopup;
  private boolean myDisposed;

  public GridCellImpl(ViewContextEx context, @NotNull GridImpl container, GridImpl.Placeholder placeholder, PlaceInGrid placeInGrid) {
    myContext = context;
    myContainer = container;

    Disposer.register(container, this);

    myPlaceInGrid = placeInGrid;
    myPlaceholder = placeholder;
    myTabs = new JBTabsImpl(myContext.getProject(), myContext.getActionManager(), myContext.getFocusManager(), container).setDataProvider(new DataProvider() {
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
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(1, -1, 1, -1));
      }
    }).setSideComponentVertical(!context.getLayoutSettings().isToolbarHorizontal())
      .setStealthTabMode(true)
      .setFocusCycle(false).setPaintFocus(true);

    myTabs.addTabMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          if (isDetached()) {
            myPopup.cancel();
            myPopup = null;
          }
          else {
            minimize(e);
          }
        }
      }
    });
    rebuildPopupGroup();
    myTabs.addListener(new TabsListener() {

      public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        if (oldSelection != null && myContext.isStateBeingRestored()) {
          saveUiState();
        }
      }

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
      if (myPlaceholder.isNull() && !isDetached()) {
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

    return tabInfo;
  }

  @Nullable
  private static TabInfo updatePresentation(TabInfo info, Content content) {
    if (info == null) return info;
    return info.setIcon(content.getIcon()).setText(content.getDisplayName()).setActions(content.getActions(), content.getPlace());
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
      } else {
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

  public java.util.List<SwitchTarget> getTargets(boolean onlyVisible) {
    return myTabs.getTargets(onlyVisible);
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
  private TabInfo getTabFor(Content content) {
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
    for (Content each : contents) {
      if (myContainer.getStateFor(each).isMinimizedInGrid()) {
        minimize(each);
      }
    }

    if (!isRestoringFromDetach() && myContainer.getTab().isDetached(myPlaceInGrid) && contents.length > 0) {
      _detach(!myContext.isStateBeingRestored()).notifyWhenDone(result);
    } else {
      result.setDone();
    }

    return result;
  }

  private Content[] getContents() {
    return myContents.getKeys().toArray(new Content[myContents.size()]);
  }

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
  }

  public void saveProportions() {
    myContainer.saveSplitterProportions(myPlaceInGrid);
  }

  private void saveState(Content content, boolean minimized) {
    View state = myContext.getStateFor(content);
    state.setMinimizedInGrid(minimized);
    state.setPlaceInGrid(myPlaceInGrid);
    state.assignTab(myContainer.getTabIndex());

    state.getTab().setDetached(myPlaceInGrid, isDetached());
  }

  public void restoreProportions() {
    myContainer.restoreLastSplitterProportions(myPlaceInGrid);
  }

  public void updateSelection(final boolean isShowing) {
    for (Content each : myContents.getKeys()) {
      final TabInfo eachTab = getTabFor(each);
      boolean isSelected = eachTab != null && myTabs.getSelectedInfo() == eachTab;
      if (isSelected && (isShowing || isDetached())) {
        myContext.getContentManager().addSelectedContent(each);
      }
      else {
        myContext.getContentManager().removeFromSelection(each);
      }
    }

    for (Content each : myMinimizedContents) {
      myContext.getContentManager().removeFromSelection(each);
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
        public ActionCallback restoreInGrid() {
          return restore(each);
        }
      });
    }
  }

  public ActionCallback detach() {
    return _detach(true);
  }

  private ActionCallback _detach(final boolean requestFocus) {
    myContext.saveUiState();

    final DimensionService dimService = DimensionService.getInstance();
    Point storedLocation = dimService.getLocation(getDimensionKey(), myContext.getProject());
    Dimension storedSize = dimService.getSize(getDimensionKey(), myContext.getProject());

    final IdeFrame frame = WindowManager.getInstance().getIdeFrame(myContext.getProject());
    final Rectangle targetBounds = frame.suggestChildFrameBounds();


    if (storedLocation != null && storedSize != null) {
      targetBounds.setLocation(storedLocation);
      targetBounds.setSize(storedSize);
    }

    final ActionCallback result = new ActionCallback();

    if (storedLocation == null || storedSize == null) {
      if (myContents.size() > 0) {
        myContext.validate(myContents.getKeys().iterator().next(), new ActiveRunnable() {
          public ActionCallback run() {
            if (!myTabs.getComponent().isShowing()) {
              detachTo(targetBounds.getLocation(), targetBounds.getSize(), false, requestFocus).notifyWhenDone(result);
            } else {
              detachForShowingTabs(requestFocus).notifyWhenDone(result);
            }

            return new ActionCallback.Done();
          }
        });

        return result;
      }
    }

    detachTo(targetBounds.getLocation(), targetBounds.getSize(), false, requestFocus).notifyWhenDone(result);

    return result;
  }

  private ActionCallback detachForShowingTabs(boolean requestFocus) {
    return detachTo(myTabs.getComponent().getLocationOnScreen(), myTabs.getComponent().getSize(), false, requestFocus);
  }

  private ActionCallback detachTo(Point screenPoint, Dimension size, boolean dragging, final boolean requestFocus) {
    if (isDetached()) {
      if (myPopup != null) {
        return new ActionCallback.Done();
      }
    }

    final Content[] contents = getContents();

    myRestoreFromDetach = new CellTransform.Restore.List();

    myRestoreFromDetach.add(myPlaceholder.detach());
    myRestoreFromDetach.add(myContainer.detach(contents));
    myRestoreFromDetach.add(new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        ensureVisible();
        return new ActionCallback.Done();
      }
    });

    myPopup = createPopup(dragging, requestFocus);
    myPopup.setSize(size);
    myPopup.setLocation(screenPoint);
    myPopup.show(myContext.getContentManager().getComponent());

    myContext.saveUiState();

    myTabs.updateTabActions(true);

    return new ActionCallback.Done();
  }

  private void ensureVisible() {
    if (myTabs.getSelectedInfo() != null) {
      myContext.select(getContentFor(myTabs.getSelectedInfo()), true);
    }
  }

  private JBPopup createPopup(boolean dragging, final boolean requestFocus) {
    Wrapper wrapper = new Wrapper(myTabs.getComponent());
    wrapper.setBorder(new EmptyBorder(1, 0, 0, 0));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, myTabs.getComponent())
      .setTitle(myContainer.getSessionName())
      .setMovable(true)
      .setRequestFocus(requestFocus)
      .setFocusable(true)
      .setResizable(true)
      .setDimensionServiceKey(myContext.getProject(), getDimensionKey(), true)
      .setCancelOnOtherWindowOpen(false)
      .setCancelOnClickOutside(false)
      .setCancelKeyEnabled(true)
      .setLocateByContent(dragging)
      .setLocateWithinScreenBounds(!dragging)
      .setCancelKeyEnabled(false)
      .setBelongsToGlobalPopupStack(false)
      .setModalContext(false)
      .setCancelCallback(new Computable<Boolean>() {
        public Boolean compute() {
          if (myDisposed || myContents.size() == 0) return Boolean.TRUE;
          myRestoreFromDetach.restoreInGrid();
          myRestoreFromDetach = null;
          myContext.saveUiState();
          myTabs.updateTabActions(true);
          return Boolean.TRUE;
        }
      });

    return builder.createPopup();
  }

  public void attach() {
    if (isDetached()) {
      myPopup.cancel();
      myPopup = null;
    }
  }


  public boolean isDetached() {
    return myRestoreFromDetach != null && !myRestoreFromDetach.isRestoringNow();
  }

  public boolean isRestoringFromDetach() {
    return myRestoreFromDetach != null && myRestoreFromDetach.isRestoringNow();
  }

  private String getDimensionKey() {
    return "GridCell.Tab." + myContainer.getTab().getIndex() + "." + myPlaceInGrid.name();
  }

  public boolean isValidForCalculatePropertions() {
    return !isDetached() && getContentCount() > 0;
  }

  public void minimize(Content content) {
    minimize(new Content[]{content});
  }

  public void minimize(MouseEvent e) {
    if (!MinimizeViewAction.isEnabled(myContext, getContents(), ViewContext.CELL_TOOLBAR_PLACE)) return;

    TabInfo tabInfo = myTabs.findInfo(e);
    if (tabInfo != null) {
      minimize(getContentFor(tabInfo));
    }
  }

  private ActionCallback restore(Content content) {
    myMinimizedContents.remove(content);
    add(content);
    updateSelection(myTabs.getComponent().getRootPane() != null);
    return new ActionCallback.Done();
  }

  public void dispose() {
    myDisposed = true;

    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }
}
