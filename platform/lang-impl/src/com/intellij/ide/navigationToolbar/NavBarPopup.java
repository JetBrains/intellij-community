// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DependentTransientComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public class NavBarPopup extends LightweightHint implements Disposable{
  private static final String JBLIST_KEY = "OriginalList";
  private static final String DISPOSED_OBJECTS = "DISPOSED_OBJECTS";

  private final NavBarPanel myPanel;
  private final int myIndex;
  private final int myItemIndex;

  public NavBarPopup(final NavBarPanel panel, int sourceItemIndex, Object[] siblings, int itemIndex, final int selectedIndex) {
    super(createPopupContent(panel, siblings));
    myPanel = panel;
    myIndex = selectedIndex;
    myItemIndex = itemIndex;
    setFocusRequestor(getComponent());
    setForceShowAsPopup(true);
    setCancelOnOtherWindowOpen(false);
    panel.installPopupHandler(getList(), selectedIndex);
    
    getList().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (SystemInfo.isWindows) {
          click(e);
        }
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        if (!SystemInfo.isWindows) {
          click(e);
        }
      }

      private void click(final MouseEvent e) {
        if (e.isConsumed()) return;
        myPanel.getModel().setSelectedIndex(selectedIndex);
        if (e.isPopupTrigger()) return;
        int idx = getList().locationToIndex(e.getPoint());
        if (idx != -1) {
          Object value = getList().getModel().getElementAt(idx);
          myPanel.navigateInsideBar(sourceItemIndex, value, SystemInfo.isMac ? e.isMetaDown() : e.isControlDown());
        }
      }
    });
  }

  @Override
  protected void onPopupCancel() {
    final JComponent component = getComponent();
    if (component != null) {
      Object o = component.getClientProperty(JBLIST_KEY);
      if (o instanceof JComponent) HintUpdateSupply.hideHint((JComponent)o);
    }
    //noinspection unchecked
    for (SelectablePanel selectablePanel : (List<SelectablePanel>)getList().getClientProperty(DISPOSED_OBJECTS)) {
      Disposer.dispose(unwrapItem(selectablePanel));
    }
    Disposer.dispose(this);
  }

  public void show(final NavBarItem item) {
    show(item, true);
  }

  private void show(final NavBarItem item, boolean checkRepaint) {
    UIEventLogger.NavBarShowPopup.log(myPanel.getProject());

    UISettings settings = UISettings.getInstance();
    int relativeY = ExperimentalUI.isNewUI()
                    && settings.getShowNavigationBarInBottom()
                    && settings.getShowStatusBar()
                    ? -getComponent().getPreferredSize().height
                    : item.getHeight();

    final RelativePoint point = new RelativePoint(item, new Point(0, relativeY));
    final Point p = point.getPoint(myPanel);
    if ((p.x == 0 || p.y == 0) && checkRepaint) { // need repaint of nav bar panel
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myPanel.getUpdateQueue().rebuildUi();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          NavBarItem updatedItem = myPanel.getItemWithObject(item.getObject());
          if (updatedItem == null) updatedItem = item;
          show(updatedItem, false); // end-less loop protection
        });
      });
    } else {
      int offset = NavBarUIManager.getUI().getPopupOffset(item);
      show(myPanel, p.x - offset, p.y, myPanel, new HintHint(myPanel, p));
      final JBList list = getList();
      //noinspection HardCodedStringLiteral
      AccessibleContextUtil.setName(list, item.getText());
      if (0 <= myIndex && myIndex < list.getItemsCount()) {
        ScrollingUtil.selectItem(list, myIndex);
      }
    }
    if (myPanel.isInFloatingMode()) {
      final Window window = SwingUtilities.windowForComponent(getList());
      if (window != null) {
        // add listener only if hint is not hidden yet
        window.addWindowFocusListener(new WindowFocusListener() {
          @Override
          public void windowGainedFocus(WindowEvent e) {
          }

          @Override
          public void windowLostFocus(WindowEvent e) {
            final Window w = e.getOppositeWindow();
            if (w != null && DialogWrapper.findInstance(w.getComponent(0)) != null) {
              myPanel.hideHint();
            }
          }
        });
      }
    }
  }

  public int getItemIndex() {
    return myItemIndex;
  }

  @Override
  public void dispose() {
  }

  private static JComponent createPopupContent(@NotNull NavBarPanel panel, Object @NotNull [] siblings) {
    class MyListRenderer implements ListCellRenderer<Object> {
      final List<SelectablePanel> selectables = new ArrayList<>();

      @Override
      public Component getListCellRendererComponent(JList<?> list, Object obj, int index, boolean isSelected, boolean cellHasFocus) {
        if (panel.isDisposed()) {
          // Don't create new NavBarItem if panel is disposed. See: IDEA-306495
          //noinspection MissingAccessibleContext
          return new SelectablePanel();
        }

        SelectablePanel selectable = null;

        for (SelectablePanel cachedSelectable : selectables) {
          NavBarItem item = unwrapItem(cachedSelectable);
          if (obj == item.getObject()) {
            item.update();
            selectable = cachedSelectable;
          }
        }

        if (selectable == null) {
          selectable = SelectablePanel.wrap(new NavBarItem(panel, obj, null, true));
          if (ExperimentalUI.isNewUI()) {
            selectable.setSelectionArc(JBUI.CurrentTheme.Popup.Selection.ARC.get());
          }
          selectables.add(selectable);
        }

        selectable.setBackground(null);
        selectable.setSelectionColor(isSelected ? UIUtil.getListSelectionBackground(cellHasFocus) : null);
        return selectable;
      }
    }

    class MyList<E> extends JBList<E> implements DependentTransientComponent, Queryable {
      @Override
      public void putInfo(@NotNull Map<? super String, ? super String> info) {
        panel.putInfo(info);
      }

      @Override
      public @NotNull Component getPermanentComponent() {
        return panel;
      }
    }

    JBList<Object> list = new MyList<>();
    list.setModel(new CollectionListModel<>(siblings));
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    MyListRenderer renderer = new MyListRenderer();
    list.putClientProperty(DISPOSED_OBJECTS, renderer.selectables);
    list.setCellRenderer(renderer);
    list.setBorder(JBUI.Borders.empty(5));
    list.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        panel.updatePopupSelection(list.getSelectedValuesList());
      }
    });

    NavBarListWrapper navBarListWrapper = new NavBarListWrapper(list);
    ListWithFilter<?> component = (ListWithFilter<?>)ListWithFilter.wrap(list, navBarListWrapper, o -> panel.getPresentation().getPresentableText(o, false));
    navBarListWrapper.updateViewportPreferredSizeIfNeeded();
    component.setAutoPackHeight(!UISettings.getInstance().getShowNavigationBarInBottom());
    component.putClientProperty(JBLIST_KEY, list);
    OpenInRightSplitAction.Companion.overrideDoubleClickWithOneClick(component);
    return component;
  }

  private static NavBarItem unwrapItem(SelectablePanel panel) {
    return (NavBarItem)panel.getComponent(0);
  }

  @NotNull
  public JBList<?> getList() {
    return ((JBList)getComponent().getClientProperty(JBLIST_KEY));
  }
}
