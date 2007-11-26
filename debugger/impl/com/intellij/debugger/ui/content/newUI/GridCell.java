package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.MutualMap;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
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

  public static final DataKey<GridCell> KEY = DataKey.create("DebuggerGridCell");

  public static final String TOOLBAR_PLACE = "GridCellToolbarPlace";
  public static final String POPUP_PLACE = "GridCellPopupPlace";

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
    myTabs.setSideComponentVertical(!context.getSettings().isToolbarHorizontal());
    myTabs.setStealthTabMode(true);
    myTabs.addTabMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          minimize();
        }
      }
    });
    DefaultActionGroup popup = new DefaultActionGroup();
    popup.add(myContext.getActionManager().getAction("Debugger.CloseView"));
    myTabs.setPopupGroup(popup, POPUP_PLACE);

  }

  public PlaceInGrid getPlaceInGrid() {
    return myPlaceInGrid;
  }

  void add(Content content) {
    if (myContents.containsKey(content)) return;
    myContents.put(content, null);

    revalidateCell();
  }

  void remove(Content content) {
    if (!myContents.containsKey(content)) return;
    myContents.remove(content);

    revalidateCell();
  }

  private void revalidateCell() {

    if (myContents.size() == 0) {
      myPlaceholder.removeAll();
    } else {
      if (myPlaceholder.isNull()) {
        myPlaceholder.setContent(myTabs);
      }

      myTabs.removeAllTabs();

      for (Content each : myContents.getKeys()) {
        myTabs.addTab(createTabInfoFor(each));
      }
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

    final TabInfo tabInfo = new TabInfo(c)
      .setIcon(content.getIcon())
      .setText(content.getDisplayName())
      .setActions(content.getActions(), content.getPlace())
      .setObject(content)
      .setPreferredFocusableComponent(content.getPreferredFocusableComponent());

    myContents.remove(content);
    myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myContext.getActionManager().getAction(DebuggerActions.DEBUGGER_VIEW);
    tabInfo.setTabActions(group, TOOLBAR_PLACE);

    return tabInfo;
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
      return KEY.getName().equals(dataId) ? GridCell.this : null;
    }
  }

}
