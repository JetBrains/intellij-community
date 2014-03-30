/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.navigationToolbar.ui.NavBarUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public class NavBarItem extends SimpleColoredComponent implements Disposable {
  //private static int count = 0;

  private final String myText;
  private final SimpleTextAttributes myAttributes;
  private final int myIndex;
  private final Icon myIcon;
  private final NavBarPanel myPanel;
  private final Object myObject;
  private final boolean isPopupElement;
  private final NavBarUI myUI;

  public NavBarItem(NavBarPanel panel, Object object, int idx, Disposable parent) {
    //count++;
    //System.out.println(count);
    myPanel = panel;
    myUI = panel.getNavBarUI();
    myObject = object;
    myIndex = idx;
    isPopupElement = idx == -1;
    if (object != null) {
      final NavBarPresentation presentation = myPanel.getPresentation();
      myText = presentation.getPresentableText(object);
      Icon icon = presentation.getIcon(object);
      myIcon = icon != null ? icon : EmptyIcon.create(5);
      myAttributes = presentation.getTextAttributes(object, false);
    } else {
      myText = "Sample";
      myIcon = PlatformIcons.DIRECTORY_CLOSED_ICON;
      myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    Disposer.register(parent == null ? panel : parent, this);

    setOpaque(false);
    setIpad(myUI.getElementIpad(isPopupElement));

    if (!isPopupElement) {
      setMyBorder(null);
      setBorder(null);
      setPaintFocusBorder(false);
    }
    update();
  }

  /**
   * item for node popup
   * @param panel
   * @param object
   */
  public NavBarItem(NavBarPanel panel, Object object, Disposable parent) {
    this(panel, object, -1, parent);
  }

  public Object getObject() {
    return myObject;
  }

  public SimpleTextAttributes getAttributes() {
    return myAttributes;
  }

  public String getText() {
    return myText;
  }

  @Override
  public Font getFont() {
    return myUI == null ? super.getFont() : myUI.getElementFont(this);
  }

  void update() {
    clear();

    setIcon(myIcon);

    final boolean focused = isFocusedOrPopupElement();
    final boolean selected = isSelected();

    setFocusBorderAroundIcon(false);
    setBackground(myUI.getBackground(selected, focused));

    Color fg = myUI.getForeground(selected, focused, isInactive());
    if (fg == null) fg = myAttributes.getFgColor();

    final Color bg = getBackground();
    append(myText, new SimpleTextAttributes(bg, fg, myAttributes.getWaveColor(), myAttributes.getStyle()));

    //repaint();
  }

  public boolean isInactive() {
    final NavBarModel model = myPanel.getModel();
    return model.getSelectedIndex() < myIndex && model.getSelectedIndex() != -1;
  }

  public boolean isPopupElement() {
    return isPopupElement;
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (isPopupElement) {
      super.doPaint(g);
    } else {
      myUI.doPaintNavBarItem(g, this, myPanel);
    }
  }


  public int doPaintText(Graphics2D g, int offset) {
    return super.doPaintText(g, offset, false);
  }



  public boolean isLastElement() {
    return myIndex == myPanel.getModel().size() - 1;
  }

  public boolean isFirstElement() {
    return myIndex == 0;
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    super.setOpaque(false);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    final Dimension offsets = myUI.getOffsets(this);
    return new Dimension(size.width + offsets.width, size.height + offsets.height);
  }

  @NotNull
  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private boolean isFocusedOrPopupElement() {
    return isFocused() || isPopupElement;
  }

  public boolean isFocused() {
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focusOwner == myPanel && !myPanel.isNodePopupShowing();
  }



  public boolean isSelected() {
    final NavBarModel model = myPanel.getModel();
    return isPopupElement ? myPanel.isSelectedInPopup(myObject) : model.getSelectedIndex() == myIndex;
  }

  @Override
  protected boolean shouldDrawBackground() {
    return isSelected() && isFocusedOrPopupElement();
  }

  @Override
  protected boolean shouldDrawMacShadow() {
    return myUI.isDrawMacShadow(isSelected(), isFocused());
  }

  @Override
  public boolean isIconOpaque() {
    return false;
  }

  private Icon wrapIcon(final Icon openIcon, final Icon closedIcon, final int idx) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        closedIcon.paintIcon(c, g, x, y);
      }

      @Override
      public int getIconWidth() {
        return closedIcon.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return openIcon.getIconHeight();
      }
    };
  }

  @Override
  public void dispose() {
    //count--;
    //System.out.println(count);
  }


  public boolean isNextSelected() {
    return myIndex == myPanel.getModel().getSelectedIndex() - 1;
  }
}
