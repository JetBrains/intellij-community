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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

public class JBOptionButton extends JButton implements MouseMotionListener {


  private static final Insets myDownIconInsets = new Insets(0, 6, 0, 4);

  private Rectangle myMoreRec;
  private Rectangle myMoreRecMouse;
  private Action[] myOptions;

  private JPopupMenu myUnderPopup;
  private JPopupMenu myAbovePopup;
  private boolean myPopupIsShowing;

  private String myOptionTooltipText;

  private Set<OptionInfo> myOptionInfos = new HashSet<OptionInfo>();
  private boolean myOkToProcessDefaultMnemonics = true;

  private IdeGlassPane myGlassPane;
  private final Disposable myDisposable = Disposer.newDisposable();

  public JBOptionButton(Action action, Action[] options) {
    super(action);
    myOptions = options;
    myMoreRec = new Rectangle(0, 0, AllIcons.General.ArrowDown.getIconWidth(), AllIcons.General.ArrowDown.getIconHeight());

    myUnderPopup = fillMenu(true);
    myAbovePopup = fillMenu(false);
    enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!ScreenUtil.isStandardAddRemoveNotify(this))
      return;
    myGlassPane = IdeGlassPaneUtil.find(this);
    if (myGlassPane != null) {
      myGlassPane.addMouseMotionPreprocessor(this, myDisposable);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (!ScreenUtil.isStandardAddRemoveNotify(this))
      return;
    Disposer.dispose(myDisposable);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    final MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, getParent());
    final boolean insideRec = getBounds().contains(event.getPoint());
    boolean buttonsNotPressed = (e.getModifiersEx() & (MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK)) == 0;
    if (!myPopupIsShowing && insideRec && buttonsNotPressed) {
      showPopup(null, false);
    } else if (myPopupIsShowing && !insideRec) {
      final Component over = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
      JPopupMenu popup = myUnderPopup.isShowing() ? myUnderPopup : myAbovePopup;
      if (over != null && popup.isShowing()) {
        final Rectangle rec = new Rectangle(popup.getLocationOnScreen(), popup.getSize());
        int delta = 15;
        rec.x -= delta;
        rec.width += delta * 2;
        rec.y -= delta;
        rec.height += delta * 2;

        final Point eventPoint = e.getPoint();
        SwingUtilities.convertPointToScreen(eventPoint, e.getComponent());
        
        if (rec.contains(eventPoint)) {
          return;
        }
      }

      closePopup();
    }
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

    myMoreRecMouse = new Rectangle(myMoreRec.x - 8, 0, getWidth() - myMoreRec.x, getHeight());
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (myMoreRec.x < event.getX()) {
      return myOptionTooltipText;
    } else {
      return super.getToolTipText(event);
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (myMoreRecMouse.contains(e.getPoint())) {
      if (e.getID() == MouseEvent.MOUSE_PRESSED) {
        if (!myPopupIsShowing) {
          togglePopup();
        }
      }
    }
    else {
      super.processMouseEvent(e);
    }
  }

  public void togglePopup() {
    if (myPopupIsShowing) {
      closePopup();
    } else {
      showPopup(null, false);
    }
  }

  public void showPopup(final Action actionToSelect, final boolean ensureSelection) {
    if (myPopupIsShowing) return;
    
    myPopupIsShowing = true;
    final Point loc = getLocationOnScreen();
    final Rectangle screen = ScreenUtil.getScreenRectangle(loc);
    final Dimension popupSize = myUnderPopup.getPreferredSize();
    final Rectangle intersection = screen.intersection(new Rectangle(new Point(loc.x, loc.y + getHeight()), popupSize));
    final boolean above = intersection.height < popupSize.height;
    int y = above ? getY() - popupSize.height : getY() + getHeight();

    final JPopupMenu popup = above ? myAbovePopup : myUnderPopup;

    final Ref<PopupMenuListener> listener = new Ref<PopupMenuListener>();
    listener.set(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (popup != null && listener.get() != null) {
          popup.removePopupMenuListener(listener.get());
        }
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myPopupIsShowing = false;
          }
        });
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });
    popup.addPopupMenuListener(listener.get());
    popup.show(this, 0, y);

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (popup == null || !popup.isShowing() || !myPopupIsShowing) return;
        
        Action selection = actionToSelect;
        if (selection == null && myOptions.length > 0 && ensureSelection) {
          selection = getAction();
        }

        if (selection == null) return;
        
        final MenuElement[] elements = popup.getSubElements();
        for (MenuElement eachElement : elements) {
          if (eachElement instanceof JMenuItem) {
            JMenuItem eachItem = (JMenuItem)eachElement;
            if (selection.equals(eachItem.getAction())) {
              final MenuSelectionManager mgr = MenuSelectionManager.defaultManager();
              final MenuElement[] path = new MenuElement[2];
              path[0] = popup;
              path[1] = eachItem;
              mgr.setSelectedPath(path);
              break;
            }
          }
        }
      }
    });
  }

  public void closePopup() {
    myUnderPopup.setVisible(false);
    myAbovePopup.setVisible(false);
  }

  private JPopupMenu fillMenu(boolean under) {
    final JPopupMenu result = new JBPopupMenu();

    if (under && myOptions.length > 0) {
      final JMenuItem mainAction = new JBMenuItem(getAction());
      configureItem(getMenuInfo(getAction()), mainAction);
      result.add(mainAction);
      result.addSeparator();
    }

    for (Action each : myOptions) {
      if (getAction() == each) continue;
      final OptionInfo info = getMenuInfo(each);
      final JMenuItem eachItem = new JBMenuItem(each);

      configureItem(info, eachItem);
      result.add(eachItem);
    }

    if (!under && myOptions.length > 0) {
      result.addSeparator();
      final JMenuItem mainAction = new JBMenuItem(getAction());
      configureItem(getMenuInfo(getAction()), mainAction);
      result.add(mainAction);
    }

    return result;
  }

  private void configureItem(OptionInfo info, JMenuItem eachItem) {
    eachItem.setText(info.myPlainText);
    if (info.myMnemonic >= 0) {
      eachItem.setMnemonic(info.myMnemonic);
      eachItem.setDisplayedMnemonicIndex(info.myMnemonicIndex);
    }
    myOptionInfos.add(info);
  }

  public boolean isOkToProcessDefaultMnemonics() {
    return myOkToProcessDefaultMnemonics;
  }


  public static class OptionInfo {

    String myPlainText;
    int myMnemonic;
    int myMnemonicIndex;
    JBOptionButton myButton;
    Action myAction;

    OptionInfo(String plainText, int mnemonic, int mnemonicIndex, JBOptionButton button, Action action) {
      myPlainText = plainText;
      myMnemonic = mnemonic;
      myMnemonicIndex = mnemonicIndex;
      myButton = button;
      myAction = action;
    }

    public String getPlainText() {
      return myPlainText;
    }

    public int getMnemonic() {
      return myMnemonic;
    }

    public int getMnemonicIndex() {
      return myMnemonicIndex;
    }

    public JBOptionButton getButton() {
      return myButton;
    }

    public Action getAction() {
      return myAction;
    }
  }
  
  private OptionInfo getMenuInfo(Action each) {
    final String text = (String)each.getValue(Action.NAME);
    int mnemonic = -1;
    int mnemonicIndex = -1;
    StringBuilder plainText = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '&' || ch == '_') {
        if (i + 1 < text.length()) {
          final char mnemonicsChar = text.charAt(i + 1);
          mnemonic = Character.toUpperCase(mnemonicsChar);
          mnemonicIndex = i;          
        }
        continue;
      }
      plainText.append(ch);
    }
    
    return new OptionInfo(plainText.toString(), mnemonic, mnemonicIndex, this, each);
    
  }

  public Set<OptionInfo> getOptionInfos() {
    return myOptionInfos;
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    boolean dark = UIUtil.isUnderDarcula();
    int off = dark ? 6 : 0;
    AllIcons.General.ArrowDown.paintIcon(this, g, myMoreRec.x - off, myMoreRec.y);
    if (dark) return;
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

  public void setOkToProcessDefaultMnemonics(boolean ok) {
    myOkToProcessDefaultMnemonics = ok;
  }
}
