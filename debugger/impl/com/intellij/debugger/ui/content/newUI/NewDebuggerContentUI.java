package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.DebuggerContentUI;
import com.intellij.debugger.ui.content.DebuggerContentUIFacade;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.AwtVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class NewDebuggerContentUI implements ContentUI, DebuggerContentInfo, Disposable, DebuggerContentUIFacade {

  private ContentManager myManager;
  private MyComponent myComponent = new MyComponent();
  private DebuggerSettings mySettings;

  private JBTabs myTabs;
  private ActionManager myActionManager;
  private String mySessionName;
  private Comparator<TabInfo> myTabsComparator = new Comparator<TabInfo>() {
    public int compare(final TabInfo o1, final TabInfo o2) {
      return ((Integer)o1.getObject()).compareTo(((Integer)o2.getObject()));
    }
  };

  public NewDebuggerContentUI(ActionManager actionManager, DebuggerSettings settings, String sessionName) {
    mySettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;

    myTabs = new JBTabs(actionManager, this);
    myTabs.setPaintBorder(false);
    //myTabs.setPaintFocus(false);

    myComponent.setContent(myTabs);
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
        }
        updateTabsUI();
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
      }
    });
  }

  @NotNull
  private Grid getGridFor(Content content, boolean createIfMissing) {
    Grid grid = findGridFor(content);
    if (grid != null || !createIfMissing) return grid;

    grid = new Grid(myActionManager, mySettings, this, mySessionName, isHorizontalToolbar());
    grid.setBorder(new EmptyBorder(1, 0, 0, 0));

    TabInfo tab = new TabInfo(grid).setObject(getContentState(content).getTab()).setText("Tab");

    myTabs.addTab(tab);
    myTabs.sortTabs(myTabsComparator);

    return grid;
  }

  private void updateTabsUI() {
    java.util.List<TabInfo> tabs = myTabs.getTabs();
    for (TabInfo each : tabs) {
      updateTabUI(each);
    }
  }

  private void updateTabUI(TabInfo tab) {
    String title = getSettings().getTabTitle(getTabIndex(tab));
    Icon icon = getSettings().getTabIcon(getTabIndex(tab));

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
          title += "|";
        }
      }
    }

    if (icon == null && contents.size() == 1) {
      icon = contents.get(0).getIcon();
    }


    tab.setText(title).setIcon(icon);
  }

  private int getTabIndex(final TabInfo tab) {
    return ((Integer)tab.getObject()).intValue();
  }

  private Grid getGridFor(TabInfo tab) {
    return (Grid)tab.getComponent();
  }

  @Nullable
  private Grid findGridFor(Content content) {
    int tabIndex = getContentState(content).getTab();
    for (TabInfo each : myTabs.getTabs()) {
      if (getTabIndex(each) == tabIndex) return getGridFor(each);
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

  public boolean isHorizontalToolbar() {
    return mySettings.isToolbarHorizontal();
  }

  public void setHorizontalToolbar(final boolean state) {
    mySettings.setToolbarHorizontal(state);
    for (Grid each : getGrids()) {
      each.setToolbarHorizontal(state);
    }
  }

  public static NewContentState getContentState(Content content) {
    return getSettings().getNewContentState(content);
  }

  private static DebuggerSettings getSettings() {
    return DebuggerSettings.getInstance();
  }

  public boolean isSingleSelection() {
    return false;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public void dispose() {
  }

  public void restoreLayout() {
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

  public ContentUI getContentUI() {
    return this;
  }
}
