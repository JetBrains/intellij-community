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
package com.intellij.ui.components;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JBOptionButton extends JButton {


  private static final Icon myDownIcon = IconLoader.getIcon("/general/arrowDown.png");
  private static final Insets myDownIconInsets = new Insets(0, 4, 0, 4);

  private Rectangle myMoreRec;
  private Rectangle myMoreRecMouse;
  private Action[] myOptions;

  private JPopupMenu myPopup;
  private String myOptionTooltipText;

  public JBOptionButton(Action action, Action[] options) {
    super(action);
    myOptions = options;
    myMoreRec = new Rectangle(0, 0, myDownIcon.getIconWidth(), myDownIcon.getIconHeight());
  }


  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.width += (myMoreRec.width + myDownIconInsets.left + myDownIconInsets.right);
    size.height += (myDownIconInsets.top + myDownIconInsets.bottom);
    return size;
  }

  @Override
  public void doLayout() {
    super.doLayout();

    Insets insets = getInsets();
    myMoreRec.x = getSize().width - myMoreRec.width - insets.right + 8;
    myMoreRec.y = (getHeight() / 2 - myMoreRec.height / 2);

    myMoreRecMouse = new Rectangle(myMoreRec.x - 6, insets.top, getWidth() - myMoreRec.x, getHeight() - insets.bottom);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (myMoreRecMouse.contains(event.getPoint())) {
      return myOptionTooltipText;
    } else {
      return super.getToolTipText(event);
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (myMoreRecMouse.contains(e.getPoint())) {
      if (e.getID() == MouseEvent.MOUSE_PRESSED) {
        if (myPopup == null) {
          togglePopup();
        }
      }
    }
    else {
      super.processMouseEvent(e);
    }
  }

  public void togglePopup() {
    if (myPopup != null) {
      closePopup();
    } else {
      showPopup();
    }
  }

  public void showPopup() {
    if (myPopup != null) return;
    
    myPopup = new JPopupMenu();
    final JMenuItem first = fillMenu();
    final Ref<PopupMenuListener> listener = new Ref<PopupMenuListener>();
    listener.set(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (myPopup !=null && listener.get() != null) {
          myPopup.removePopupMenuListener(listener.get());
        }
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myPopup = null;
          }
        });
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });
    myPopup.addPopupMenuListener(listener.get());
    myPopup.show(this, myMoreRec.x, getY() + getHeight() - getInsets().bottom);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (first != null) {
          myPopup.setSelected(first);
        }
      }
    });
  }

  public void closePopup() {
    myPopup.setVisible(false);
  }

  private JMenuItem fillMenu() {
    JMenuItem first = null;
    for (Action each : myOptions) {
      if (getAction() == each) continue;
      String plainText = getMenuText(each);
      final JMenuItem eachItem = new JMenuItem(each);

      if (first == null) {
        first = eachItem;
      }
      eachItem.setText(plainText.toString());
      myPopup.add(eachItem);
    }

    if (myOptions.length > 0) {
      myPopup.addSeparator();
      final JMenuItem mainAction = new JMenuItem(getAction());
      mainAction.setText(getMenuText(getAction()));
      myPopup.add(mainAction);
    }
    
    return first;
  }

  private String getMenuText(Action each) {
    final String text = (String)each.getValue(Action.NAME);
    StringBuilder plainText = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '&' || ch == '_') {
        continue;
      }
      plainText.append(ch);
    }
    return plainText.toString();
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    myDownIcon.paintIcon(this, g, myMoreRec.x, myMoreRec.y);

    int y1 = myMoreRec.y - 2;
    int y2 = myMoreRec.y + myMoreRec.height + 2;

    final Insets insets = getInsets();

    if (y1 < getInsets().top) {
      y1 = insets.top;
    }

    if (y2 > getHeight() - insets.bottom) {
      y2 = getHeight() - insets.bottom;
    }

    final int x = myMoreRec.x - 4;
    UIUtil.drawDottedLine(((Graphics2D)g), x, y1, x, y2, null, Color.darkGray);
  }

  public void setOptionTooltipText(String text) {
    myOptionTooltipText = text;
  }
}
