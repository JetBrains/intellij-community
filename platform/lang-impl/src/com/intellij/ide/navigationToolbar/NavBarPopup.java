/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarPopup extends LightweightHint implements Disposable{
  private static final String JBLIST_KEY = "OriginalList";
  private static final String DISPOSED_OBJECTS = "DISPOSED_OBJECTS";

  private final NavBarPanel myPanel;
  private final int myIndex;

  public NavBarPopup(final NavBarPanel panel, Object[] siblings, final int selectedIndex) {
    super(createPopupContent(panel, siblings));
    myPanel = panel;
    myIndex = selectedIndex;
    setFocusRequestor(getComponent());
    setForceShowAsPopup(true);
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
        Object value = getList().getSelectedValue();
        if (value != null) {
          myPanel.navigateInsideBar(value);
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
    for (Disposable disposable : ((List<Disposable>)getList().getClientProperty(DISPOSED_OBJECTS))) {
      Disposer.dispose(disposable);
    }
    Disposer.dispose(this);
  }

  public void show(final NavBarItem item) {
    show(item, true);
  }

  private void show(final NavBarItem item, boolean checkRepaint) {
    final RelativePoint point = new RelativePoint(item, new Point(0, item.getHeight()));
    final Point p = point.getPoint(myPanel);
    if (p.x == 0 && p.y == 0 && checkRepaint) { // need repaint of nav bar panel
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myPanel.getUpdateQueue().rebuildUi();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          show(item, false); // end-less loop protection
        });
      });
    } else {
      int offset = NavBarUIManager.getUI().getPopupOffset(item);
      show(myPanel, p.x - offset, p.y, myPanel, new HintHint(myPanel, p));
      final JBList list = getList();
      AccessibleContextUtil.setName(list, item.getText());
      if (0 <= myIndex && myIndex < list.getItemsCount()) {
        ScrollingUtil.selectItem(list, myIndex);
      }
    }
    if (myPanel.isInFloatingMode()) {
      final Window window = SwingUtilities.windowForComponent(getList());
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

  @Override
  public void dispose() {
  }

  private static JComponent createPopupContent(NavBarPanel panel, Object[] siblings) {
    class MyList<E> extends JBList<E> implements DataProvider, Queryable {
      @Override
      public void putInfo(@NotNull Map<String, String> info) {
        panel.putInfo(info);
      }

      @Nullable
      @Override
      public Object getData(String dataId) {
        return panel.getDataImpl(dataId, this, () -> JBIterable.from(getSelectedValuesList()));
      }
    }
    JBList<Object> list = new MyList<>();
    list.setModel(new CollectionListModel<>(siblings));
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    List<Disposable> disposables = new ArrayList<>();
    list.putClientProperty(DISPOSED_OBJECTS, disposables);
    list.installCellRenderer(obj -> {
      final NavBarItem navBarItem = new NavBarItem(panel, obj, null);
      disposables.add(navBarItem);
      return navBarItem;
    });
    list.setBorder(JBUI.Borders.empty(5));
    installMoveAction(list, panel, -1, KeyEvent.VK_LEFT);
    installMoveAction(list, panel, 1, KeyEvent.VK_RIGHT);
    installEnterAction(list, panel, KeyEvent.VK_ENTER);
    installEscapeAction(list, panel, KeyEvent.VK_ESCAPE);
    JComponent component = ListWithFilter.wrap(list, new NavBarListWrapper(list), o -> panel.getPresentation().getPresentableText(o));
    component.putClientProperty(JBLIST_KEY, list);
    return component;
  }

  private static void installEnterAction(JBList list, NavBarPanel panel, int keyCode) {
    AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.navigateInsideBar(list.getSelectedValue());
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }

  private static void installEscapeAction(JBList list, NavBarPanel panel, int keyCode) {
    AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.cancelPopup();
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }

  @NotNull
  public JBList<?> getList() {
    return ((JBList)getComponent().getClientProperty(JBLIST_KEY));
  }

  private static void installMoveAction(JBList list, NavBarPanel panel, int direction, int keyCode) {
    AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.cancelPopup();
        panel.shiftFocus(direction);
        panel.restorePopup();
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }
}
