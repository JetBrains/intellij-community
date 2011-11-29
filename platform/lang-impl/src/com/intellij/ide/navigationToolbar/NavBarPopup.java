/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarPopup extends LightweightHint implements Disposable{
  private static final String JBLIST_KEY = "OriginalList";
  private static final String NAV_BAR_POPUP = "NAV_BAR_POPUP";
  private final NavBarPanel myPanel;
  private int myIndex;
  private static final String DISPOSED_OBJECTS = "DISPOSED_OBJECTS";

  public NavBarPopup(final NavBarPanel panel, Object[] siblings, final int selectedIndex) {
    super(createPopupContent(panel, siblings));
    myPanel = panel;
    myIndex = selectedIndex;
    setFocusRequestor(getComponent());
    setForceShowAsPopup(true);
    ListenerUtil.addMouseListener(getComponent(), new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        if (SystemInfo.isWindows) {
          click(e);
        }
      }

      public void mousePressed(final MouseEvent e) {
        if (!SystemInfo.isWindows) {
          click(e);
        }
      }

      private void click(final MouseEvent e) {
        if (e.getComponent() != getList()) return;
        if (!e.isConsumed() && e.isPopupTrigger()) {
          myPanel.getModel().setSelectedIndex(selectedIndex);
          IdeFocusManager.getInstance(myPanel.getProject()).requestFocus(myPanel, true);
          myPanel.rightClick(selectedIndex);
          e.consume();
        } else {
          final Object value = getList().getSelectedValue();
          if (value != null) {
            myPanel.navigateInsideBar(value);
          }
        }
      }
    });
  }

  @Override
  protected void onPopupCancel() {
    final JComponent component = getComponent();
    if (component != null) {
      final Object o = component.getClientProperty(JBLIST_KEY);
      if (o instanceof JBListWithHintProvider) {
        ((JBListWithHintProvider)o).hideHint();
      }
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
    final RelativePoint point = new RelativePoint(item, new Point(-NavBarUIManager.getUI().getPopupOffset(item), item.getHeight()));
    final Point p = point.getPoint(myPanel);
    if (p.x == 0 && p.y == 0 && checkRepaint) { // need repaint of nav bar panel
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          show(item, false); // end-less loop protection
        }
      });
    } else {
      show(myPanel, p.x, p.y, myPanel, new HintHint(myPanel, p));
      final JBList list = getList();
      if (0 <= myIndex && myIndex < list.getItemsCount()) {
       ListScrollingUtil.selectItem(list, myIndex);
      }
    }
  }

  @Override
  public void dispose() {
  }

  private static JComponent createPopupContent(final NavBarPanel panel, Object[] siblings) {
    final JBListWithHintProvider list = new NavbarPopupList(panel, siblings);
    list.setDataProvider(new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        return panel.getData(dataId);
      }
    });
    final List<Disposable> disposables = new ArrayList<Disposable>();
    list.putClientProperty(DISPOSED_OBJECTS, disposables);
    list.installCellRenderer(new NotNullFunction<Object, JComponent>() {
      @NotNull
      @Override
      public JComponent fun(Object obj) {
        final NavBarItem navBarItem = new NavBarItem(panel, obj, null);
        disposables.add(navBarItem);
        return navBarItem;
      }
    });
    list.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    installMoveAction(list, panel, -1, KeyEvent.VK_LEFT);
    installMoveAction(list, panel, 1, KeyEvent.VK_RIGHT);
    installEnterAction(list, panel, KeyEvent.VK_ENTER);
    installEscapeAction(list, panel, KeyEvent.VK_ESCAPE);
    final JComponent component = ListWithFilter.wrap(list, new NavBarListWrapper(list), new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        return NavBarPresentation.getPresentableText(o, panel.getWindow());
      }
    });
    component.putClientProperty(JBLIST_KEY, list);
    return component;
  }

  private static void installEnterAction(final JBList list, final NavBarPanel panel, int keyCode) {
    final AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.navigateInsideBar(list.getSelectedValue());
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }

  private static void installEscapeAction(final JBList list, final NavBarPanel panel, int keyCode) {
    final AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.cancelPopup();
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }

  public Object getSelectedValue() {
    return getList().getSelectedValue();
  }
  
  public Object[] getSelectedValues() {
    return getList().getSelectedValues();
  }

  public JBList getList() {
    return ((JBList)getComponent().getClientProperty(JBLIST_KEY));
  }

  private static void installMoveAction(JBList list, final NavBarPanel panel, final int direction, final int keyCode) {
    final AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.cancelPopup();
        panel.shiftFocus(direction);
        panel.restorePopup();
      }
    };
    list.registerKeyboardAction(action, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED);
  }

  private static class NavbarPopupList extends JBListWithHintProvider implements Queryable {
    private NavBarPanel myPanel;

    public NavbarPopupList(NavBarPanel panel, Object[] siblings) {
      super(siblings);
      myPanel= panel;
    }

    @Override
    public void putInfo(Map<String, String> info) {
      myPanel.putInfo(info);
    }

    @Override
    protected PsiElement getPsiElementForHint(Object selectedValue) {
      return selectedValue instanceof PsiElement ? (PsiElement) selectedValue : null;
    }
  }
}
