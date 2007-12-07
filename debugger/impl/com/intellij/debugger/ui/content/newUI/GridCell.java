package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.MutualMap;
import com.intellij.ui.components.panels.NonOpaquePanel;
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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

public class GridCell {

  private Grid myContainer;

  private MutualMap<Content, TabInfo> myContents = new MutualMap<Content, TabInfo>(true);
  private Set<Content> myMinimizedContents = new HashSet<Content>();

  private JBTabs myTabs;
  private Grid.Placeholder myPlaceholder;
  private PlaceInGrid myPlaceInGrid;

  private ViewContext myContext;

  public GridCell(ViewContext context, Grid container, Grid.Placeholder placeholder, PlaceInGrid placeInGrid) {
    myContext = context;
    myContainer = container;

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
          minimize();
        }
      }
    });
    myTabs.setPopupGroup((ActionGroup)myContext.getActionManager().getAction(DebuggerActions.DEBUGGER_VIEW), ViewContext.CELL_POPUP_PLACE);
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
  }

  void remove(Content content) {
    if (!myContents.containsKey(content)) return;

    final TabInfo info = getTabFor(content);
    myContents.remove(content);

    revalidateCell(new Runnable() {
      public void run() {
        myTabs.removeTab(info, false);
      }
    });
  }

  private void revalidateCell(Runnable contentAction) {
    if (myContents.size() == 0) {
      myPlaceholder.removeAll();
      myTabs.removeAllTabs();
    } else {
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
      .setPreferredFocusableComponent(content.getPreferredFocusableComponent());

    myContents.remove(content);
    myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myContext.getActionManager().getAction(DebuggerActions.DEBUGGER_VIEW);
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
        return new Content[] {myContent};
      } else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
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

    Content[] contents = myContents.getKeys().toArray(new Content[myContents.size()]);
    for (Content each : contents) {
      if (myContainer.getStateFor(each).isMinimizedInGrid()) {
        minimize();
      }
    }
  }

  public void saveUiState() {
    myContainer.saveSplitterProportions(myPlaceInGrid);

    for (Content each : myContents.getKeys()) {
      saveState(each, false);
    }

    for (Content each : myMinimizedContents) {
      saveState(each, true);
    }
  }

  private void saveState(Content content, boolean minimized) {
    NewContentState state = myContext.getStateFor(content);
    state.setMinimizedInGrid(minimized);
    state.setPlaceInGrid(myPlaceInGrid);
    state.setTab(myContainer.getTabIndex());
  }

  private void restoreProportions() {
    myContainer.restoreLastSplitterProportions(myPlaceInGrid);
  }

  public void updateSelection(final boolean isShowing) {
    for (Content each : myContents.getKeys()) {
      final TabInfo eachTab = getTabFor(each);
      if (eachTab != null && myTabs.getSelectedInfo() == eachTab && isShowing) {
        myContext.getContentManager().addSelectedContent(each);
      } else {
        myContext.getContentManager().removeFromSelection(each);
      }
    }
  }

  public void minimize() {
    myContext.saveUiState();

    final Content content = getContentFor(myTabs.getSelectedInfo());
    myMinimizedContents.add(content);
    remove(content);
    myContainer.minimize(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        return restore(content);
      }
    });
  }

  private ActionCallback restore(Content content) {
    myMinimizedContents.remove(content);
    add(content);
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
          Content content = getContentFor(target);
          if (content != null) {
            return new Content[] {content};
          }
        }
      } else if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
        return myContext;
      }

      return super.getData(dataId);
    }
  }

}
