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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
* @author Konstantin Bulenkov
*/
class NavBarItem extends SimpleColoredComponent implements Disposable {
  private static Image SEPARATOR_ACTIVE = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorActive.png"));
  private static Image SEPARATOR_PASSIVE = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorPassive.png"));
  private static Image SEPARATOR_GRADIENT = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorGradient.png"));
  //private static int count = 0;

  private final String myText;
  private final SimpleTextAttributes myAttributes;
  private final int myIndex;
  private final Icon myIcon;
  private final NavBarPanel myPanel;
  private Object myObject;
  private final boolean isPopupElement;
  private JBInsets myPadding;

  public NavBarItem(NavBarPanel panel, Object object, int idx, Disposable parent) {
    //count++;
    //System.out.println(count);
    myPanel = panel;
    myObject = object;
    myIndex = idx;
    isPopupElement = idx == -1;
    if (object != null) {
      Icon closedIcon = NavBarPresentation.getIcon(object, false);
      Icon openIcon = NavBarPresentation.getIcon(object, true);

      if (closedIcon == null && openIcon != null) closedIcon = openIcon;
      if (openIcon == null && closedIcon != null) openIcon = closedIcon;
      if (openIcon == null) {
        openIcon = closedIcon = EmptyIcon.create(5);
      }
      final NavBarPresentation presentation = myPanel.getPresentation();
      myText = NavBarPresentation.getPresentableText(object, myPanel.getWindow());
      myIcon = wrapIcon(openIcon, closedIcon, idx);
      myAttributes = presentation.getTextAttributes(object, false);
    } else {
      myText = "Sample";
      myIcon = PlatformIcons.DIRECTORY_OPEN_ICON;
      myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    Disposer.register(parent == null ? panel : parent, this);

    setOpaque(false);
    setFont(UIUtil.isUnderAquaLookAndFeel() ? UIUtil.getLabelFont().deriveFont(11.0f) : getFont());
    if (isPopupElement || !NavBarPanel.isDecorated()) {
      setIpad(new Insets(1,2,1,2));
    } else {
      setIpad(new Insets(0,0,0,0));
      setMyBorder(null);
      setBorder(null);
      setPaintFocusBorder(false);
    }
    update();
    myPadding = new JBInsets(3, 3, 3, 3);
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

  void update() {
    clear();

    setIcon(myIcon);
    final boolean focused = isFocusedOrPopupElement();

    final NavBarModel model = myPanel.getModel();
    final boolean selected = isSelected();

    if (!NavBarPanel.isDecorated()) {
      setPaintFocusBorder(selected && !isPopupElement && myPanel.isNodePopupActive());
    }
    setFocusBorderAroundIcon(false);

    setBackground(selected && focused
                  ? UIUtil.getListSelectionBackground()
                  : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground()));

    final Color fg = selected && focused
                     ? UIUtil.getListSelectionForeground()
                     : model.getSelectedIndex() < myIndex && model.getSelectedIndex() != -1
                       ? UIUtil.getInactiveTextColor()
                       : myAttributes.getFgColor();

    final Color bg = selected && focused ? UIUtil.getListSelectionBackground() : myAttributes.getBgColor();
    append(myText, new SimpleTextAttributes(bg, fg, myAttributes.getWaveColor(), myAttributes.getStyle()));

    repaint();
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (isPopupElement || !NavBarPanel.isDecorated()) {
      super.doPaint(g);
    } else {
      doPaintDecorated(g);
    }
  }

  private void doPaintDecorated(Graphics2D g) {
    Icon icon = myIcon;
    final Color bg = isSelected() && isFocused()
                      ? UIUtil.getListSelectionBackground()
                      : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
    final Color c = UIUtil.getListSelectionBackground();
    final Color selBg = new Color(c.getRed(), c.getGreen(), c.getBlue(), getAlpha());
    int w = getWidth();
    int h = getHeight();
    if (/*!UIUtil.isUnderAquaLookAndFeel() ||*/ myPanel.isInFloatingMode() || (isSelected() && myPanel.hasFocus())) {
      g.setPaint(isSelected() && isFocused() ? selBg : bg);
      g.fillRect(0, 0, w - (isLastElement() /*|| !UIUtil.isUnderAquaLookAndFeel()*/ ? 0 : getDecorationOffset()), h);
    }
    final int offset = isFirstElement() ? getFirstElementLeftOffset() : 0;
    final int iconOffset = myPadding.left + offset;
    icon.paintIcon(this, g, iconOffset, (h - icon.getIconHeight()) / 2);
    final int textOffset = icon.getIconWidth() + myPadding.width() + offset;    
    int x = doPaintText(g, textOffset, false);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
    g.translate(x, 0);
    Path2D.Double path;
    int off = getDecorationOffset();
    if (isFocused()) {
      if (isSelected() && !isLastElement()) {
        path = new Path2D.Double();
        g.translate(2, 0);
        path.moveTo(0, 0);
        path.lineTo(off, h / 2);              // |\
        path.lineTo(0, h);                    // |/
        path.lineTo(0, 0);
        g.setColor(selBg);
        g.fill(path);
        g.translate(-2, 0);
      }

      if (/*!UIUtil.isUnderAquaLookAndFeel() || */myPanel.isInFloatingMode() || isNextSelected()) {
        if (! isLastElement()) {
          path = new Path2D.Double();
          path.moveTo(0, 0);
          path.lineTo(off, h / 2);                // ___
          path.lineTo(0, h);                      // \ |
          path.lineTo(off + 2, h);                // /_|
          path.lineTo(off + 2, 0);
          path.lineTo(0, 0);
          g.setColor(isNextSelected() ? selBg : UIUtil.getListBackground());
          //if (UIUtil.isUnderAquaLookAndFeel() && isNextSelected() || !UIUtil.isUnderAquaLookAndFeel()) {
            g.fill(path);
          //}
        }
      }
    }
    if (! isLastElement() && ((!isSelected() && !isNextSelected()) || !myPanel.hasFocus())) {
      Image img = SEPARATOR_PASSIVE;
      final UISettings settings = UISettings.getInstance();
      if (settings.SHOW_NAVIGATION_BAR) {
        img = SEPARATOR_GRADIENT;
      }
      g.drawImage(img, null, null);
    }
  }

  private static short getAlpha() {
    if ((UIUtil.isUnderAlloyLookAndFeel() && !UIUtil.isUnderAlloyIDEALookAndFeel())
      || UIUtil.isUnderMetalLookAndFeel() || UIUtil.isUnderMetalLookAndFeel()){
      return 255;
    }
    return 150;
  }

  private static int getDecorationOffset() {
    return 11;
  }
  
  private static int getFirstElementLeftOffset() {
    return 6;
  }

  private boolean isLastElement() {
    return myIndex == myPanel.getModel().size() - 1;
  }

  private boolean isFirstElement() {
    return myIndex == 0;
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    super.setOpaque(false);
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    if (! isPopupElement && NavBarPanel.isDecorated()) {
      size.width += getDecorationOffset() + myPadding.width() + (isFirstElement() ? getFirstElementLeftOffset() : 0);
      size.height += myPadding.height();
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private boolean isFocusedOrPopupElement() {
    return isFocused() || isPopupElement;
  }

  private boolean isFocused() {
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
    return UIUtil.isUnderAquaLookAndFeel() && !isSelected();
  }

  @Override
  public boolean isIconOpaque() {
    return false;
  }

  private Icon wrapIcon(final Icon openIcon, final Icon closedIcon, final int idx) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (myPanel.getModel().getSelectedIndex() == idx && myPanel.isNodePopupActive()) {
          openIcon.paintIcon(c, g, x, y);
        }
        else {
          closedIcon.paintIcon(c, g, x, y);
        }
      }

      public int getIconWidth() {
        return openIcon.getIconWidth();
      }

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
    

  private boolean isNextSelected() {
    return myIndex == myPanel.getModel().getSelectedIndex() - 1;
  }
}
