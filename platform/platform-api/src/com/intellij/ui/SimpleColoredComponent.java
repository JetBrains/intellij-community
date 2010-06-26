/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * This is high performance Swing component which represents an icon
 * with a colored text. The text consists of fragments. Each
 * text fragment has its own color (foreground) and font style.
 *
 * @author Vladimir Kondratyev
 */
public class SimpleColoredComponent extends JComponent implements Accessible {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SimpleColoredComponent");

  private final ArrayList<String> myFragments;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private ArrayList<Object> myFragmentTags = null;

  /**
   * Component's icon. It can be <code>null</code>.
   */
  private Icon myIcon;
  /**
   * Internal padding
   */
  private Insets myIpad;
  /**
   * Gap between icon and text. It is used only if icon is defined.
   */
  private int myIconTextGap;
  /**
   * Defines whether the focus border around the text is painted or not.
   * For example, text can have a border if the component represents a selected item
   * in focused JList.
   */
  private boolean myPaintFocusBorder;
  /**
   * Defines whether the focus border around the text extends to icon or not
   */
  private boolean myFocusBorderAroundIcon;
  /**
   * This is the border around the text. For example, text can have a border
   * if the component represents a selected item in a focused JList.
   * Border can be <code>null</code>.
   */
  private final MyBorder myBorder;

  private int myMainTextLastIndex = -1;

  private final Map<Integer, Integer> myAligns;

  private boolean myIconOpaque = true;

  private boolean myAutoInvalidate = true;

  private AccessibleContext myContext = new MyAccessibleContext();

  public SimpleColoredComponent() {
    myFragments = new ArrayList<String>(3);
    myAttributes = new ArrayList<SimpleTextAttributes>(3);
    myIpad = new Insets(1, 2, 1, 2);
    myIconTextGap = 2;
    myBorder = new MyBorder();
    myAligns = new HashMap<Integer, Integer>(10);
    setOpaque(true);
  }

  public final void append(@NotNull String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   */
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    append(fragment, attributes, myMainTextLastIndex < 0);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   */
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    synchronized (this) {
      myFragments.add(fragment);
      myAttributes.add(attributes);
      if (isMainText) {
        myMainTextLastIndex = myFragments.size() - 1;
      }
    }
    revalidateAndRepaint();
  }

  private void revalidateAndRepaint() {
    if (myAutoInvalidate) {
      revalidate();
    }

    repaint();
  }

  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, Object tag) {
    synchronized (this) {
      append(fragment, attributes);
      if (myFragmentTags == null) {
        myFragmentTags = new ArrayList<Object>();
      }
      while(myFragmentTags.size() < myFragments.size()-1) {
        myFragmentTags.add(null);
      }
      myFragmentTags.add(tag);
    }
    revalidateAndRepaint();
  }

  public synchronized void appendAlign(int alignWidth) {
    final int alignIndex = myFragments.size()-1;
    myAligns.put(alignIndex, alignWidth);
  }

  /**
   * Clear all special attributes of <code>SimpleColoredComponent</code>.
   * They are icon, text fragments and their attributes, "paint focus border".
   */
  public void clear() {
    synchronized (this) {
      myIcon = null;
      myPaintFocusBorder = false;
      myFragments.clear();
      myAttributes.clear();
      myFragmentTags = null;
      myMainTextLastIndex = -1;
      myAligns.clear();
    }
    revalidateAndRepaint();
  }

  /**
   * @return component's icon. This method returns <code>null</code>
   *         if there is no icon.
   */
  public final Icon getIcon() {
    return myIcon;
  }

  /**
   * Sets a new component icon
   */
  public final void setIcon(final Icon icon) {
    myIcon = icon;
    revalidateAndRepaint();
  }

  /**
   * @return "leave" (internal) internal paddings of the component
   */
  public Insets getIpad() {
    return myIpad;
  }

  /**
   * Sets specified internal paddings
   */
  public void setIpad(final Insets ipad) {
    myIpad = ipad;

    revalidateAndRepaint();
  }

  /**
   * @return gap between icon and text
   */
  public int getIconTextGap() {
    return myIconTextGap;
  }

  /**
   * Sets a new gap between icon and text
   *
   * @throws java.lang.IllegalArgumentException
   *          if the <code>iconTextGap</code>
   *          has a negative value
   */
  public void setIconTextGap(final int iconTextGap) {
    if (iconTextGap < 0) {
      throw new IllegalArgumentException("wrong iconTextGap: " + iconTextGap);
    }
    myIconTextGap = iconTextGap;

    revalidateAndRepaint();
  }

  /**
   * Sets whether focus border is painted or not
   */
  protected final void setPaintFocusBorder(final boolean paintFocusBorder) {
    myPaintFocusBorder = paintFocusBorder;

    repaint();
  }

  /**
   * Sets whether focus border extends to icon or not. If so then
   * component also extends the selection.
   */
  protected final void setFocusBorderAroundIcon(final boolean focusBorderAroundIcon) {
    myFocusBorderAroundIcon = focusBorderAroundIcon;

    repaint();
  }

  public boolean isIconOpaque() {
    return myIconOpaque;
  }

  public void setIconOpaque(final boolean iconOpaque) {
    myIconOpaque = iconOpaque;

    repaint();
  }

  public Dimension getPreferredSize() {
    return computePreferredSize(false);

  }

  public synchronized Object getFragmentTag(int index) {
    if (myFragmentTags != null && index < myFragmentTags.size()) {
      return myFragmentTags.get(index);
    }
    return null;
  }

  public final synchronized Dimension computePreferredSize(final boolean mainTextOnly) {
    // Calculate width
    int width = myIpad.left;

    if (myIcon != null) {
      width += myIcon.getIconWidth() + myIconTextGap;
    }

    final Insets borderInsets = myBorder.getBorderInsets(this);
    width += borderInsets.left;

    Font font = getFont();
    if (font == null) {
      font = UIManager.getFont("Label.font");
    }

    LOG.assertTrue(font != null);

    for (int i = 0; i < myAttributes.size(); i++) {
      SimpleTextAttributes attributes = myAttributes.get(i);
      if (font.getStyle() != attributes.getStyle()) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getStyle());
      }
      final FontMetrics metrics = getFontMetrics(font);
      width += metrics.stringWidth(myFragments.get(i));

      final Integer fixedWidth = myAligns.get(i);
      if (fixedWidth != null && width < fixedWidth.intValue()) {
        width = fixedWidth.intValue();
      }

      if (mainTextOnly && myMainTextLastIndex >= 0 && i == myMainTextLastIndex) break;
    }
    width += myIpad.right + borderInsets.right;

    // Calculate height
    int height = myIpad.top + myIpad.bottom;

    final FontMetrics metrics = getFontMetrics(font);
    int textHeight = metrics.getHeight();
    textHeight += borderInsets.top + borderInsets.bottom;

    if (myIcon != null) {
      height += Math.max(myIcon.getIconHeight(), textHeight);
    }
    else {
      height += textHeight;
    }

    // Take into accound that the component itself can have a border
    final Insets insets = getInsets();
    width += insets.left + insets.right;
    height += insets.top + insets.bottom;

    return new Dimension(width, height);
  }

  public int findFragmentAt(int x) {
    int curX = myIpad.left;
    if (myIcon != null) {
      curX += myIcon.getIconWidth() + myIconTextGap;
    }

    Font font = getFont();
    LOG.assertTrue(font != null);

    for (int i = 0; i < myAttributes.size(); i++) {
      SimpleTextAttributes attributes = myAttributes.get(i);
      if (font.getStyle() != attributes.getStyle()) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getStyle());
      }
      final FontMetrics metrics = getFontMetrics(font);
      final int curWidth = metrics.stringWidth(myFragments.get(i));
      if (x >= curX && x < curX + curWidth) {
        return i;
      }
      curX += curWidth;
      final Integer fixedWidth = myAligns.get(i);
      if (fixedWidth != null && curX < fixedWidth.intValue()) {
        curX = fixedWidth.intValue();
      }
    }
    return -1;
  }

  protected void paintComponent(final Graphics g) {
    try {
      doPaint(g);
    }
    catch (RuntimeException e) {
      LOG.error(logSwingPath(), e);
      throw e;
    }
  }

  protected synchronized void doPaint(final Graphics g) {
    checkCanPaint(g);
    int xOffset = 0;

    // Paint icon and its background
    final Icon icon = myIcon;   // guard against concurrent modification (IDEADEV-12635)
    if (icon != null) {
      final Container parent = getParent();
      Color iconBackgroundColor = null;
      if (isIconOpaque()) {
        if (parent != null && !myFocusBorderAroundIcon && !UIUtil.isFullRowSelectionLAF()) {
          iconBackgroundColor = parent.getBackground();
        }
        else {
          iconBackgroundColor = getBackground();
        }
      }

      if (iconBackgroundColor != null) {
        g.setColor(iconBackgroundColor);
        g.fillRect(0, 0, icon.getIconWidth() + myIpad.left + myIconTextGap, getHeight());
      }

      icon.paintIcon(this, g, myIpad.left, (getHeight() - icon.getIconHeight()) / 2);

      xOffset += myIpad.left + icon.getIconWidth() + myIconTextGap;
    }

    if (isOpaque()) {
      // Paint text background
      g.setColor(getBackground());
      g.fillRect(xOffset, 0, getWidth() - xOffset, getHeight());
    }

    // If there is no icon, then we have to add left internal padding
    if (xOffset == 0) {
      xOffset = myIpad.left;
    }

    int textStart = xOffset;
    xOffset += myBorder.getBorderInsets(this).left;

    // Paint text
    UIUtil.applyRenderingHints(g);
    for (int i = 0; i < myFragments.size(); i++) {
      final SimpleTextAttributes attributes = myAttributes.get(i);
      Font font = getFont();
      if (font.getStyle() != attributes.getStyle()) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getStyle());
      }
      g.setFont(font);
      final FontMetrics metrics = getFontMetrics(font);

      final String fragment = myFragments.get(i);
      final int fragmentWidth = metrics.stringWidth(fragment);

      final Color bgColor = attributes.getBgColor();
      if (isOpaque() && bgColor != null) {
        g.setColor(bgColor);
        g.fillRect(xOffset, 0, fragmentWidth, getHeight());
      }

      Color color = attributes.getFgColor();
      if (color == null) { // in case if color is not defined we have to get foreground color from Swing hierarchy
        color = getForeground();
      }
      if (!isEnabled()) {
        color = UIUtil.getTextInactiveTextColor();
      }
      g.setColor(color);

      final int textBaseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
      g.drawString(fragment, xOffset, textBaseline);

      // 1. Strikeout effect
      if (attributes.isStrikeout()) {
        final int strikeOutAt = textBaseline + (metrics.getDescent() - metrics.getAscent()) / 2;
        UIUtil.drawLine(g, xOffset, strikeOutAt, xOffset + fragmentWidth, strikeOutAt);
      }
      // 2. Waved effect
      if (attributes.isWaved()) {
        if (attributes.getWaveColor() != null) {
          g.setColor(attributes.getWaveColor());
        }
        final int wavedAt = textBaseline + 1;
        for (int x = xOffset; x <= xOffset + fragmentWidth; x += 4) {
          UIUtil.drawLine(g, x, wavedAt, x + 2, wavedAt + 2);
          UIUtil.drawLine(g, x + 3, wavedAt + 1, x + 4, wavedAt);
        }
      }
      // 3. Underline
      if (attributes.isUnderline()) {
        final int underlineAt = textBaseline + 1;
        UIUtil.drawLine(g, xOffset, underlineAt, xOffset + fragmentWidth, underlineAt);
      }
      // 4. Bold Dotted Line
      if (attributes.isBoldDottedLine()) {
        final int dottedAt = SystemInfo.isMac ? textBaseline : textBaseline + 1;
        final Color lineColor = attributes.getWaveColor();
        UIUtil.drawBoldDottedLine((Graphics2D)g, xOffset, xOffset + fragmentWidth, dottedAt, bgColor, lineColor, isOpaque());
      }

      final Integer fixedWidth = myAligns.get(i);
      if (fixedWidth != null && fragmentWidth < fixedWidth.intValue()) {
      //if (fixedWidth != null) {
        xOffset += fixedWidth.intValue();
      } else {
        xOffset += fragmentWidth;
      }
    }

    // Paint focus border around the text and icon (if necessary)
    if (myPaintFocusBorder) {
      if (myFocusBorderAroundIcon || icon == null) {
        myBorder.paintBorder(this, g, 0, 0, getWidth(), getHeight());
      }
      else {
        myBorder.paintBorder(this, g, textStart, 0, getWidth() - textStart, getHeight());
      }
    }
  }

  private static void checkCanPaint(Graphics g) {
    if (UIUtil.isPrinting(g)) return;

    /* wtf??
    if (!isDisplayable()) {
      LOG.assertTrue(false, logSwingPath());
    }
    */
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      application.assertIsDispatchThread();
    }
    else if (!SwingUtilities.isEventDispatchThread()) {
      throw new RuntimeException(Thread.currentThread().toString());
    }
  }

  private String logSwingPath() {
    //noinspection HardCodedStringLiteral
    final StringBuilder buffer = new StringBuilder("Components hierarchy:\n");
    for (Container c = this; c != null; c = c.getParent()) {
      buffer.append('\n');
      buffer.append(c.toString());
    }
    return buffer.toString();
  }

  protected void setBorderInsets(Insets insets) {
    myBorder.setInsets(insets);

    revalidateAndRepaint();
  }

  private static final class MyBorder implements Border {
    private Insets myInsets;

    public MyBorder() {
      myInsets = new Insets(1, 1, 1, 1);
    }

    public void setInsets(final Insets insets) {
      myInsets = insets;
    }

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      g.setColor(Color.BLACK);
      UIUtil.drawDottedRectangle(g, x, y, x + width - 1, y + height - 1);
    }

    public Insets getBorderInsets(final Component c) {
      return myInsets;
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }

  @Override
  public String toString() {
    StringBuffer result = new StringBuffer();
    for (String each : myFragments) {
      result.append(each);
    }

    return result.toString();
  }


  public void change(@NotNull Runnable runnable, boolean autoInvalidate) {
    boolean old = myAutoInvalidate;
    myAutoInvalidate = autoInvalidate;
    try {
      runnable.run();
    } finally {
      myAutoInvalidate = old;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    return myContext;
  }

  private static class MyAccessibleContext extends AccessibleContext {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.AWT_COMPONENT;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      return new AccessibleStateSet();
    }

    @Override
    public int getAccessibleIndexInParent() {
      return 0;
    }

    @Override
    public int getAccessibleChildrenCount() {
      return 0;
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      return null;
    }

    @Override
    public Locale getLocale() throws IllegalComponentStateException {
      return Locale.getDefault();
    }
  }
}
