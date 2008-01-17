package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.ui.content.newUI.actions.CloseViewAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
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
import java.util.Set;

public class GridCell implements Disposable {

  private Grid myContainer;

  private MutualMap<Content, TabInfo> myContents = new MutualMap<Content, TabInfo>(true);
  private Set<Content> myMinimizedContents = new HashSet<Content>();

  private JBTabs myTabs;
  private Grid.Placeholder myPlaceholder;
  private PlaceInGrid myPlaceInGrid;

  private ViewContext myContext;
  private CellTransform.Restore.List myRestoreFromDetach;
  private JBPopup myPopup;
  private boolean myDisposed;

  public GridCell(ViewContext context, Grid container, Grid.Placeholder placeholder, PlaceInGrid placeInGrid) {
    myContext = context;
    myContainer = container;

    Disposer.register(container, this);

    myPlaceInGrid = placeInGrid;
    myPlaceholder = placeholder;
    myTabs = new MyTabs(context.getProject(), container);
    myTabs.setUiDecorator(new JBTabs.UiDecorator() {
      public JBTabs.UiDecoration getDecoration() {
        return new JBTabs.UiDecoration(null, new Insets(0, -1, 0, -1));
      }
    });
    myTabs.setSideComponentVertical(!context.getSettings().getLayoutSettings().isToolbarHorizontal());
    myTabs.setStealthTabMode(true);
    myTabs.addTabMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          if (isDetached()) {
            myPopup.cancel();
          }
          else {
            minimize(e);
          }
        }
      }
    });
    myTabs.setPopupGroup((ActionGroup)myContext.getActionManager().getAction(DebuggerActions.DEBUGGER_VIEW_POPUP),
                         ViewContext.CELL_POPUP_PLACE);
    myTabs.addListener(new TabsListener() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        if (!myTabs.isShowing()) return;

        if (newSelection != null) {
          newSelection.stopAlerting();
        }
      }
    });
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

    updateSelection(myTabs.getRootPane() != null);
  }

  void remove(Content content) {
    if (!myContents.containsKey(content)) return;

    final TabInfo info = getTabFor(content);
    myContents.remove(content);

    revalidateCell(new Runnable() {
      public void run() {
        myTabs.removeTab(info, true);
      }
    });

    updateSelection(myTabs.getRootPane() != null);
  }

  private void revalidateCell(Runnable contentAction) {
    if (myContents.size() == 0) {
      myPlaceholder.removeAll();
      myTabs.removeAllTabs();
    }
    else {
      if (myPlaceholder.isNull()) {
        myPlaceholder.setContent(myTabs);
      }

      contentAction.run();
    }

    restoreProportions();

    myTabs.revalidate();
    myTabs.repaint();
  }

  void setHideTabs(boolean hide) {
    myTabs.setHideTabs(hide);
  }

  private TabInfo createTabInfoFor(Content content) {
    final JComponent c = content.getComponent();

    NewDebuggerContentUI.removeScrollBorder(c);

    final TabInfo tabInfo = new TabInfo(new ProviderWrapper(content, myContext))
      .setIcon(content.getIcon())
      .setText(content.getDisplayName())
      .setActions(content.getActions(), content.getPlace())
      .setObject(content)
      .setPreferredFocusableComponent(content.getPreferredFocusableComponent())
      .setActionsContextComponent(content.getActionsContextComponent());

    myContents.remove(content);
    myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myContext.getActionManager().getAction(DebuggerActions.DEBUGGER_VIEW_TOOLBAR);
    tabInfo.setTabLabelActions(group, ViewContext.CELL_TOOLBAR_PLACE);

    return tabInfo;
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    final TabInfo tabInfo = myContents.getValue(content);
    return tabInfo != null ? myTabs.select(tabInfo, requestFocus) : new ActionCallback.Done();
  }

  public void alert(final Content content) {
    if (myMinimizedContents.contains(content)) return;

    TabInfo tab = getTabFor(content);
    if (tab == null) return;
    if (myTabs.getSelectedInfo() != tab) {
      tab.fireAlert();
    }
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
      if (ViewContext.CONTENT_KEY.getName().equals(dataId)) {
        return new Content[]{myContent};
      }
      else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
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
    myTabs.setSideComponentVertical(!horizontal);
  }

  public void restoreLastUiState() {
    restoreProportions();

    Content[] contents = getContents();
    for (Content each : contents) {
      if (myContainer.getStateFor(each).isMinimizedInGrid()) {
        minimize(each);
      }
    }

    if (!isRestoringFromDetach() && myContainer.getTab().isDetached(myPlaceInGrid)) {
      detach();
    }
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
      boolean isShowing = myTabs.getRootPane() != null;
      updateSelection(isShowing);
      myContainer.minimize(each, new CellTransform.Restore() {
        public ActionCallback restoreInGrid() {
          return restore(each);
        }
      });
    }
  }

  public void detach() {
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

    if (storedLocation == null || storedSize == null) {
      if (myContents.size() > 0) {
        myContext.validate(myContents.getKeys().iterator().next(), new ActionCallback.Runnable() {
          public ActionCallback run() {
            if (!myTabs.isShowing()) {
              detachTo(targetBounds.getLocation(), targetBounds.getSize(), false);
            } else {
              detachForShowingTabs();
            }

            return new ActionCallback.Done();
          }
        });

        return;
      }
    }

    detachTo(targetBounds.getLocation(), targetBounds.getSize(), false);
  }

  private void detachForShowingTabs() {
    detachTo(myTabs.getLocationOnScreen(), myTabs.getSize(), false);
  }

  private void detachTo(Point screenPoint, Dimension size, boolean dragging) {
    if (isDetached()) return;

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

    myPopup = createPopup(dragging);
    myPopup.setSize(size);
    myPopup.setLocation(screenPoint);
    myPopup.show(myContext.getContentManager().getComponent());

    myContext.saveUiState();

    myTabs.updateTabActions(true);
  }

  private void ensureVisible() {
    if (myTabs.getSelectedInfo() != null) {
      myContext.select(getContentFor(myTabs.getSelectedInfo()), true);
    }
  }

  private JBPopup createPopup(boolean dragging) {
    Wrapper wrapper = new Wrapper(myTabs);
    wrapper.setBorder(new EmptyBorder(1, 0, 0, 0));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, myTabs)
      .setTitle(myContainer.getSessionName())
      .setMovable(true)
      .setRequestFocus(true)
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
          if (myDisposed) return Boolean.TRUE;
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

  public void minimize(Content content) {
    minimize(new Content[]{content});
  }

  public void minimize(MouseEvent e) {
    if (!CloseViewAction.isEnabled(myContext, getContents(), ViewContext.CELL_TOOLBAR_PLACE)) return;

    TabInfo tabInfo = myTabs.findInfo(e);
    if (tabInfo != null) {
      minimize(getContentFor(tabInfo));
    }
  }

  private ActionCallback restore(Content content) {
    myMinimizedContents.remove(content);
    add(content);
    updateSelection(myTabs.getRootPane() != null);
    return new ActionCallback.Done();
  }

  private class MyTabs extends JBTabs implements DataProvider {
    public MyTabs(final Project project, final Grid container) {
      super(project, myContext.getActionManager(), container);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (ViewContext.CONTENT_KEY.getName().equals(dataId)) {
        TabInfo target = myTabs.getTargetInfo();
        if (target != null) {
          return new Content[]{getContentFor(target)};
        }
      }
      else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
        return myContext;
      }

      return super.getData(dataId);
    }
  }

  public void dispose() {
    myDisposed = true;

    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }
}
