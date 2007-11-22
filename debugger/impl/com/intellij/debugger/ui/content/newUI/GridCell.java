package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.MutualMap;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class GridCell {

  public static final DataKey<GridCell> KEY = DataKey.create("DebuggerGridCell");

  private Grid myContainer;

  private MutualMap<Content, TabInfo> myContents = new MutualMap<Content, TabInfo>(true);
  private Set<Content> myMinimizedContents = new HashSet<Content>();

  private JBTabs myTabs;
  private Grid.Placeholder myPlaceholder;
  private PlaceInGrid myPlaceInGrid;
  private ContentManager myContentManager;

  private ActionManager myActionManager;


  public GridCell(Grid container, Project project, Grid.Placeholder placeholder, boolean horizontalToolbars, PlaceInGrid placeInGrid) {
    myContainer = container;

    myContentManager = container.myContentManager;
    myActionManager = container.myActionManager;
    myPlaceInGrid = placeInGrid;
    myPlaceholder = placeholder;
    myTabs = new MyTabs(project, container);
    myTabs.setUiDecorator(new JBTabs.UiDecorator() {
      public JBTabs.UiDecoration getDecoration() {
        return new JBTabs.UiDecoration(null, new Insets(0, -1, 0, -1));
      }
    });
    myTabs.setSideComponentVertical(!horizontalToolbars);
    myTabs.setStealthTabMode(true);

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

    restoreLastUiState();

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

    myContents.put(content, tabInfo);

    ActionGroup group = (ActionGroup)myActionManager.getAction(DebuggerActions.DEBUGGER_VIEW);
    tabInfo.setTabActions(group);

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
    myContainer.restoreLastSplitterProportions(myPlaceInGrid);
  }

  public void updateSelection(final boolean isShowing) {
    for (Content each : myContents.getKeys()) {
      final TabInfo eachTab = getTabFor(each);
      if (eachTab != null && myTabs.getSelectedInfo() == eachTab && isShowing) {
        myContentManager.addSelectedContent(each);
      } else {
        myContentManager.removeFromSelection(each);
      }
    }
  }

  public void minimize() {
    final Content content = getContentFor(myTabs.getSelectedInfo());
    myContainer.minimize(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        return restore(content);
      }
    });
  }

  private ActionCallback restore(Content content) {
    return new ActionCallback.Done();
  }

  private class MyTabs extends JBTabs implements DataProvider {
    public MyTabs(final Project project, final Grid container) {
      super(project, container.myActionManager, container);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      return KEY.getName().equals(dataId) ? GridCell.this : null;
    }
  }

}
