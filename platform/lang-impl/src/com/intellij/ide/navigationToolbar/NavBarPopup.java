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

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.HintHint;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarPopup extends LightweightHint {
  private final NavBarPanel myPanel;

  public NavBarPopup(NavBarPanel panel, Object[] siblings, final int selectedIndex) {
    super(createPopupContent(panel, siblings, selectedIndex));
    myPanel = panel;
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
        if (!e.isConsumed() && e.isPopupTrigger()) {
          myPanel.getModel().setSelectedIndex(selectedIndex);
          IdeFocusManager.getInstance(myPanel.getProject()).requestFocus(myPanel, true);
          myPanel.rightClick(selectedIndex);
          e.consume();
        }
      }
    });
  }

  public void show(final NavBarItem item) {
    final RelativePoint point = new RelativePoint(item, new Point(0, item.getHeight()));
    final Point p = point.getPoint(myPanel);
    show(myPanel, p.x, p.y, myPanel, new HintHint(myPanel, p));
  }

  private static JBList createPopupContent(final NavBarPanel panel, Object[] siblings, int selectedIndex) {
    final JBList list = new JBList(siblings);
    list.setDataProvider(new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        return panel.getData(dataId);
      }
    });
    list.installCellRenderer(new NotNullFunction<Object, JComponent>() {
      @NotNull
      @Override
      public JComponent fun(Object obj) {
        return new NavBarItem(panel, obj);
      }
    });
    list.setBorder(IdeBorderFactory.createEmptyBorder(5,5,5,5));
    list.setSelectedIndex(selectedIndex);
    list.registerKeyboardAction(createMoveAction(panel, -1), KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);
    list.registerKeyboardAction(createMoveAction(panel,  1), KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_FOCUSED);

    list.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        panel.cancelPopup();
      }
    });

    return list;
  }

  public Object getSelectedValue() {
    return ((JBList)getComponent()).getSelectedValue();
  }

  private static Action createMoveAction(final NavBarPanel panel, final int direction) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.cancelPopup();
        panel.shiftFocus(direction);
        panel.restorePopup();
      }
    };
  }

  private static class CancelNavBarPopup extends AbstractAction implements FocusListener {
    private final NavBarPanel myPanel;

    private CancelNavBarPopup(NavBarPanel panel) {
      myPanel = panel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      cancelPopup();
    }

    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(FocusEvent e) {
      cancelPopup();
    }

    private void cancelPopup() {
      myPanel.cancelPopup();
    }
  }
}
