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
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This is high performance Swing component which represents an icon
 * with a colored text. The text consists of fragments. Each
 * text fragment has its own color (foreground) and font style.
 *
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized", "UnusedDeclaration"})
public class SimpleColoredComponent extends JComponent implements Accessible {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SimpleColoredComponent");
  public static final Color STYLE_SEARCH_MATCH_BACKGROUND = new Color(250, 250, 250, 140);

  private final ArrayList<String> myFragments;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private ArrayList<Object> myFragmentTags = null;
  
  public static final int FRAGMENT_ICON = -2;

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
  private Border myBorder;

  private int myMainTextLastIndex = -1;

  private final Map<Integer, Integer> myAligns;

  private boolean myIconOpaque = true;

  private boolean myAutoInvalidate = true;

  private AccessibleContext myContext = new MyAccessibleContext();

  private boolean myIconOnTheRight = false;

  public SimpleColoredComponent() {
    myFragments = new ArrayList<String>(3);
    myAttributes = new ArrayList<SimpleTextAttributes>(3);
    myIpad = new Insets(1, 2, 1, 2);
    myIconTextGap = 2;
    myBorder = new MyBorder();
    myAligns = new HashMap<Integer, Integer>(10);
    setOpaque(true);
  }

  public boolean isIconOnTheRight() {
    return myIconOnTheRight;
  }

  public void setIconOnTheRight(boolean iconOnTheRight) {
    myIconOnTheRight = iconOnTheRight;
  }

  public final void append(@NotNull String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   * @param fragment text fragment
   * @param attributes text attributes
   */
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    append(fragment, attributes, myMainTextLastIndex < 0);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   * @param fragment text fragment
   * @param attributes text attributes
   * @param isMainText main text of not
   */
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    _append(fragment, attributes, isMainText);
    revalidateAndRepaint();
  }
  
  private synchronized void _append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    myFragments.add(fragment);
    myAttributes.add(attributes);
    if (isMainText) {
      myMainTextLastIndex = myFragments.size() - 1;
    }    
  }

  private void revalidateAndRepaint() {
    if (myAutoInvalidate) {
      revalidate();
    }

    repaint();
  }

  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, Object tag) {
    _append(fragment, attributes, tag);
    revalidateAndRepaint();
  }

  private synchronized void _append(String fragment, SimpleTextAttributes attributes, Object tag) {
    append(fragment, attributes);
    if (myFragmentTags == null) {
      myFragmentTags = new ArrayList<Object>();
    }
    while(myFragmentTags.size() < myFragments.size()-1) {
      myFragmentTags.add(null);
    }
    myFragmentTags.add(tag);
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
    _clear();
    revalidateAndRepaint();
  }

  private synchronized void _clear() {
    myIcon = null;
    myPaintFocusBorder = false;
    myFragments.clear();
    myAttributes.clear();
    myFragmentTags = null;
    myMainTextLastIndex = -1;
    myAligns.clear();
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
   * @param icon icon
   */
  public final void setIcon(final @Nullable Icon icon) {
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
   * @param ipad insets
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
   * @param iconTextGap the gap between text and icon
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

  public Border getMyBorder() {
    return myBorder;
  }

  public void setMyBorder(@Nullable Border border) {
    myBorder = border;
  }

  /**
   * Sets whether focus border is painted or not
   * @param paintFocusBorder <code>true</code> or <code>false</code>
   */
  protected final void setPaintFocusBorder(final boolean paintFocusBorder) {
    myPaintFocusBorder = paintFocusBorder;

    repaint();
  }

  /**
   * Sets whether focus border extends to icon or not. If so then
   * component also extends the selection.
   * @param focusBorderAroundIcon <code>true</code> or <code>false</code>
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

  @Override
  public Dimension getPreferredSize() {
    return computePreferredSize(false);

  }

  @Override
  public Dimension getMinimumSize() {
    return computePreferredSize(false);
  }

  @Nullable
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

    final Insets borderInsets = myBorder != null ? myBorder.getBorderInsets(this) : new Insets(0,0,0,0);
    width += borderInsets.left;

    Font font = getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }

    LOG.assertTrue(font != null);

    int baseSize = font.getSize();
    boolean wasSmaller = false;
    for (int i = 0; i < myAttributes.size(); i++) {
      SimpleTextAttributes attributes = myAttributes.get(i);
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
      }
      wasSmaller = isSmaller;
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

  /**
   * Returns the index of text fragment at the specified X offset.
   *
   * @param x the offset
   * @return the index of the fragment, {@link #FRAGMENT_ICON} if the icon is at the offset, or -1 if nothing is there.
   */
  public int findFragmentAt(int x) {
    int curX = myIpad.left;
    if (myIcon != null && !myIconOnTheRight) {
      final int iconRight = myIcon.getIconWidth() + myIconTextGap;
      if (x < iconRight) {
        return FRAGMENT_ICON;
      }
      curX += iconRight;
    }

    Font font = getFont();
    LOG.assertTrue(font != null);

    int baseSize = font.getSize();
    boolean wasSmaller = false;
    for (int i = 0; i < myAttributes.size(); i++) {
      SimpleTextAttributes attributes = myAttributes.get(i);
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
      }
      wasSmaller = isSmaller;

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

    if (myIcon != null && myIconOnTheRight) {
      curX += myIconTextGap;
      if (x >= curX && x < curX + myIcon.getIconWidth()) {
        return FRAGMENT_ICON;
      }
    }
    return -1;
  }

  protected void paintComponent(final Graphics g) {
    try {
      _doPaint(g);
    }
    catch (RuntimeException e) {
      LOG.error(logSwingPath(), e);
      throw e;
    }
  }

  private synchronized void _doPaint(final Graphics g) {
    checkCanPaint(g);
    doPaint((Graphics2D)g);
  }
  
  protected void doPaint(final Graphics2D g) {
    int offset = 0;
    final Icon icon = myIcon; // guard against concurrent modification (IDEADEV-12635)
    if (icon != null && !myIconOnTheRight) {
      doPaintIcon(g, icon, 0);
      offset += myIpad.left + icon.getIconWidth() + myIconTextGap;
    }

    doPaintTextBackground(g, offset);
    offset = doPaintText(g, offset, myFocusBorderAroundIcon || icon == null);
    if (icon != null && myIconOnTheRight) {
      doPaintIcon(g, icon, offset);
    }
  }
  

  protected void doPaintTextBackground(Graphics2D g, int offset) {
    if (isOpaque() || shouldDrawBackground()) {
      g.setColor(getBackground());
      g.fillRect(offset, 0, getWidth() - offset, getHeight());
    }
  }

  protected void doPaintIcon(Graphics2D g, Icon icon, int offset) {
    final Container parent = getParent();
    Color iconBackgroundColor = null;
    if (isOpaque() || isIconOpaque()) {
      if (parent != null && !myFocusBorderAroundIcon && !UIUtil.isFullRowSelectionLAF()) {
        iconBackgroundColor = parent.getBackground();
      }
      else {
        iconBackgroundColor = getBackground();
      }
    }

    if (iconBackgroundColor != null) {
      g.setColor(iconBackgroundColor);
      g.fillRect(offset, 0, icon.getIconWidth() + myIpad.left + myIconTextGap, getHeight());
    }

    paintIcon(g, icon, offset + myIpad.left);
  }

  protected int doPaintText(Graphics2D g, int offset, boolean focusAroundIcon) {
    // If there is no icon, then we have to add left internal padding
    if (offset == 0) {
      offset = myIpad.left;
    }

    int textStart = offset;
    if (myBorder != null) {
      offset += myBorder.getBorderInsets(this).left;
    }

    final List<Object[]> searchMatches = new ArrayList<Object[]>();
    
    UIUtil.applyRenderingHints(g);
    applyAdditionalHints(g);

    final Font ownFont = getFont();
    int baseSize = ownFont != null ? ownFont.getSize() : g.getFont().getSize();
    boolean wasSmaller = false;
    for (int i = 0; i < myFragments.size(); i++) {
      final SimpleTextAttributes attributes = myAttributes.get(i);

      Font font = g.getFont();
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
      }
      wasSmaller = isSmaller;

      g.setFont(font);
      final FontMetrics metrics = g.getFontMetrics(font);

      final String fragment = myFragments.get(i);
      final int fragmentWidth = metrics.stringWidth(fragment);

      final Color bgColor = attributes.getBgColor();
      if (isOpaque() && bgColor != null) {
        g.setColor(bgColor);
        g.fillRect(offset, 0, fragmentWidth, getHeight());
      }

      Color color = attributes.getFgColor();
      if (color == null) { // in case if color is not defined we have to get foreground color from Swing hierarchy
        color = getForeground();
      }
      if (!isEnabled()) {
        color = UIUtil.getInactiveTextColor();
      }
      g.setColor(color);

      final int textBaseline = getTextBaseLine(metrics, getHeight());

      if (!attributes.isSearchMatch()) {
        if (shouldDrawMacShadow()) {
          g.setColor(STYLE_SEARCH_MATCH_BACKGROUND);
          g.drawString(fragment, offset, textBaseline + 1);
        }

        g.setColor(color);
        g.drawString(fragment, offset, textBaseline);
      }

      // 1. Strikeout effect
      if (attributes.isStrikeout()) {
        final int strikeOutAt = textBaseline + (metrics.getDescent() - metrics.getAscent()) / 2;
        UIUtil.drawLine(g, offset, strikeOutAt, offset + fragmentWidth, strikeOutAt);
      }
      // 2. Waved effect
      if (attributes.isWaved()) {
        if (attributes.getWaveColor() != null) {
          g.setColor(attributes.getWaveColor());
        }
        final int wavedAt = textBaseline + 1;
        for (int x = offset; x <= offset + fragmentWidth; x += 4) {
          UIUtil.drawLine(g, x, wavedAt, x + 2, wavedAt + 2);
          UIUtil.drawLine(g, x + 3, wavedAt + 1, x + 4, wavedAt);
        }
      }
      // 3. Underline
      if (attributes.isUnderline()) {
        final int underlineAt = textBaseline + 1;
        UIUtil.drawLine(g, offset, underlineAt, offset + fragmentWidth, underlineAt);
      }
      // 4. Bold Dotted Line
      if (attributes.isBoldDottedLine()) {
        final int dottedAt = SystemInfo.isMac ? textBaseline : textBaseline + 1;
        final Color lineColor = attributes.getWaveColor();
        UIUtil.drawBoldDottedLine(g, offset, offset + fragmentWidth, dottedAt, bgColor, lineColor, isOpaque());
      }

      if (attributes.isSearchMatch()) {
        searchMatches.add(new Object[] {offset, offset + fragmentWidth, textBaseline, fragment, g.getFont()});
      }

      final Integer fixedWidth = myAligns.get(i);
      if (fixedWidth != null && fragmentWidth < fixedWidth.intValue()) {
        //if (fixedWidth != null) {
        offset = fixedWidth.intValue();
      } else {
        offset += fragmentWidth;
      }
    }

    // Paint focus border around the text and icon (if necessary)
    if (myPaintFocusBorder && myBorder != null) {
      if (focusAroundIcon) {
        myBorder.paintBorder(this, g, 0, 0, getWidth(), getHeight());
      } else {
        myBorder.paintBorder(this, g, textStart, 0, getWidth() - textStart, getHeight());
      }
    }

    // draw search matches after all
    for (final Object[] info: searchMatches) {
      UIUtil.drawSearchMatch(g, (Integer) info[0], (Integer) info[1], getHeight());
      g.setFont((Font) info[4]);

      if (shouldDrawMacShadow()) {
        g.setColor(new Color(250, 250, 250, 140));
        g.drawString((String) info[3], (Integer) info[0], (Integer) info[2] + 1);
      }

      g.setColor(new Color(50, 50, 50));
      g.drawString((String) info[3], (Integer) info[0], (Integer) info[2]);
    }
    return offset;
  }

  protected boolean shouldDrawMacShadow() {
    return false;
  }

  protected boolean shouldDrawBackground() {
    return false;
  }
  
  protected void paintIcon(Graphics g, Icon icon, int offset) {
    icon.paintIcon(this, g, offset, (getHeight() - icon.getIconHeight()) / 2);
  }

  protected void applyAdditionalHints(final Graphics g) {
  }

  @Override
  public int getBaseline(int width, int height) {
    super.getBaseline(width, height);
    return getTextBaseLine(getFontMetrics(getFont()), height);
  }

  private static int getTextBaseLine(FontMetrics metrics, final int height) {
    return (height - metrics.getHeight()) / 2 + metrics.getAscent();
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
    if (myBorder instanceof MyBorder) {
      ((MyBorder)myBorder).setInsets(insets);
    }

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
    final StringBuilder result = new StringBuilder();
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

    @Nullable
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
