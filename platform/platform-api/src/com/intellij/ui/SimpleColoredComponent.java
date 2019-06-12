// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This is high performance Swing component which represents an icon
 * with a colored text. The text consists of fragments. Each
 * text fragment has its own color (foreground) and font style.
 *
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized"})
public class SimpleColoredComponent extends JComponent implements Accessible, ColoredTextContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SimpleColoredComponent");

  public static final int FRAGMENT_ICON = -2;

  private final List<ColoredFragment> myFragments;
  private ColoredFragment myCurrentFragment;
  private Font myLayoutFont;

  /**
   * Component's icon. It can be {@code null}.
   */
  private Icon myIcon;
  /**
   * Internal padding
   */
  private Insets myIpad;
  /**
   * Gap between icon and text. It is used only if icon is defined.
   */
  protected int myIconTextGap;
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
   * Border can be {@code null}.
   */
  private Border myBorder;

  private int myMainTextLastIndex = -1;

  @JdkConstants.HorizontalAlignment private int myTextAlign = SwingConstants.LEFT;

  private boolean myIconOpaque;

  private boolean myAutoInvalidate = !(this instanceof TreeCellRenderer);

  private boolean myIconOnTheRight;
  private boolean myTransparentIconBackground;

  public SimpleColoredComponent() {
    myFragments = new ArrayList<>(3);
    myIpad = JBInsets.create(1, 2);
    myIconTextGap = JBUIScale.scale(2);
    myBorder = JBUI.Borders.empty(1, UIUtil.isUnderWin10LookAndFeel() ? 0 : 1);
    setOpaque(true);
    updateUI();
  }

  @Override
  public void updateUI() {
    UISettings.setupComponentAntialiasing(this);
  }

  @NotNull
  public ColoredIterator iterator() {
    return new MyIterator();
  }

  @NotNull
  public ColoredIterator iterator(int fromIndex) {
    MyIterator iterator = new MyIterator();
    iterator.myIndex = fromIndex - 1;
    return iterator;
  }

  @SuppressWarnings("unused")
  public boolean isIconOnTheRight() {
    return myIconOnTheRight;
  }

  public void setIconOnTheRight(boolean iconOnTheRight) {
    myIconOnTheRight = iconOnTheRight;
  }

  @NotNull
  public final SimpleColoredComponent append(@NotNull String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    return this;
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified {@code attributes}.
   *
   * @param fragment   text fragment
   * @param attributes text attributes
   */
  @Override
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    append(fragment, attributes, myMainTextLastIndex < 0);
  }

  /**
   * Appends text fragment and sets it's end offset and alignment.
   * See SimpleColoredComponent#appendTextPadding for details
   * @param fragment text fragment
   * @param attributes text attributes
   * @param padding end offset of the text
   * @param align alignment between current offset and padding
   */
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, int padding, @JdkConstants.HorizontalAlignment int align) {
    append(fragment, attributes, myMainTextLastIndex < 0);
    appendTextPadding(padding, align);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified {@code attributes}.
   *
   * @param fragment   text fragment
   * @param attributes text attributes
   * @param isMainText main text of not
   */
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    _append(fragment, attributes, isMainText);
    revalidateAndRepaint();
  }

  private void _append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    synchronized (myFragments) {
      myCurrentFragment = new ColoredFragment(fragment, attributes);
      myFragments.add(myCurrentFragment);
      if (isMainText) {
        myMainTextLastIndex = myFragments.size() - 1;
      }
    }
  }

  void revalidateAndRepaint() {
    if (myAutoInvalidate) {
      revalidate();
    }

    repaint();
  }

  @Override
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, Object tag) {
    _append(fragment, attributes, tag);
    revalidateAndRepaint();
  }

  private void _append(String fragment, SimpleTextAttributes attributes, Object tag) {
    synchronized (myFragments) {
      append(fragment, attributes);
      if (tag != null) myCurrentFragment.tag = tag;
    }
  }

  /**
   * fragment width isn't a right name, it is actually a padding
   * @deprecated remove in IDEA 16
   */
  @Deprecated
  public void appendFixedTextFragmentWidth(int width) {
    appendTextPadding(width);
  }

  public void appendTextPadding(int padding) {
    appendTextPadding(padding, SwingConstants.LEFT);
  }

  /**
   * @param padding end offset that will be set after drawing current text fragment
   * @param align alignment of the current text fragment, if it is SwingConstants.RIGHT
   *              or SwingConstants.TRAILING then the text fragment will be aligned to the right at
   *              the padding, otherwise it will be aligned to the left
   */
  public void appendTextPadding(int padding, @JdkConstants.HorizontalAlignment int align) {
    synchronized (myFragments) {
      if (myCurrentFragment != null) {
        myCurrentFragment.padding = padding;
        myCurrentFragment.alignment = align;
      }
    }
  }

  public void setTextAlign(@JdkConstants.HorizontalAlignment int align) {
    myTextAlign = align;
  }

  /**
   * Clear all special attributes of {@code SimpleColoredComponent}.
   * They are icon, text fragments and their attributes, "paint focus border".
   */
  public void clear() {
    _clear();
    revalidateAndRepaint();
  }

  private void _clear() {
    synchronized (myFragments) {
      myIcon = null;
      myPaintFocusBorder = false;
      myFragments.clear();
      myCurrentFragment = null;
      myMainTextLastIndex = -1;
    }
  }

  /**
   * @return component's icon. This method returns {@code null}
   * if there is no icon.
   */
  public final Icon getIcon() {
    return myIcon;
  }

  /**
   * Sets a new component icon
   *
   * @param icon icon
   */
  @Override
  public final void setIcon(@Nullable final Icon icon) {
    myIcon = icon;
    revalidateAndRepaint();
  }

  /**
   * @return "leave" (internal) internal paddings of the component
   */
  @NotNull
  public Insets getIpad() {
    return myIpad;
  }

  /**
   * Sets specified internal paddings
   *
   * @param ipad insets
   */
  public void setIpad(@NotNull Insets ipad) {
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
   * @throws IllegalArgumentException if the {@code iconTextGap}
   *                                            has a negative value
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
   *
   * @param paintFocusBorder {@code true} or {@code false}
   */
  protected final void setPaintFocusBorder(final boolean paintFocusBorder) {
    myPaintFocusBorder = paintFocusBorder;

    repaint();
  }

  /**
   * Sets whether focus border extends to icon or not. If so then
   * component also extends the selection.
   *
   * @param focusBorderAroundIcon {@code true} or {@code false}
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
  @NotNull
  public Dimension getPreferredSize() {
    return computePreferredSize(false);
  }

  @Override
  @NotNull
  public Dimension getMinimumSize() {
    return computePreferredSize(false);
  }

  @Nullable
  public Object getFragmentTag(int index) {
    synchronized (myFragments) {
      if (0 <= index && index < myFragments.size()) {
        return myFragments.get(index).tag;
      }
    }
    return null;
  }

  @NotNull
  public final Dimension computePreferredSize(final boolean mainTextOnly) {
    synchronized (myFragments) {
      // Calculate width
      int width = myIpad.left;

      if (myIcon != null) {
        width += myIcon.getIconWidth() + myIconTextGap;
      }

      final Insets borderInsets = myBorder != null ? myBorder.getBorderInsets(this) : JBUI.emptyInsets();
      width += borderInsets.left;

      Font font = getBaseFont();

      width += computeTextWidth(font, mainTextOnly);
      width += myIpad.right + borderInsets.right;

      // Take into account that the component itself can have a border
      final Insets insets = getInsets();
      if (insets != null) {
        width += insets.left + insets.right;
      }

      int height = computePreferredHeight();

      return new Dimension(width, height);
    }
  }

  final synchronized int computePreferredHeight() {
    int height = myIpad.top + myIpad.bottom;

    Font font = getBaseFont();

    final FontMetrics metrics = getFontMetrics(font);
    int textHeight = Math.max(getMinHeight(), metrics.getHeight()); //avoid too narrow rows

    Insets borderInsets = myBorder != null ? myBorder.getBorderInsets(this) : JBUI.emptyInsets();
    textHeight += borderInsets.top + borderInsets.bottom;

    height += myIcon == null ? textHeight : Math.max(myIcon.getIconHeight(), textHeight);

    // Take into account that the component itself can have a border
    final Insets insets = getInsets();
    if (insets != null) {
      height += insets.top + insets.bottom;
    }

    return height;
  }

  protected int getMinHeight() {
    return JBUIScale.scale(16);
  }

  private Rectangle computePaintArea() {
    Rectangle area = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(area, getInsets());
    JBInsets.removeFrom(area, myIpad);
    return area;
  }

  private float computeTextWidth(@NotNull Font font, final boolean mainTextOnly) {
    float result = 0;
    int baseSize = font.getSize();
    boolean wasSmaller = false;
    for (int i = 0; i < myFragments.size(); i++) {
      ColoredFragment fragment = myFragments.get(i);
      SimpleTextAttributes attributes = fragment.attributes;
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
      }
      wasSmaller = isSmaller;

      result += computeStringWidth(fragment, font);

      final int fixedWidth = fragment.padding;
      if (fixedWidth > 0 && result < fixedWidth) {
        result = fixedWidth;
      }
      if (mainTextOnly && myMainTextLastIndex >= 0 && i == myMainTextLastIndex) break;
    }
    return result;
  }

  @NotNull
  private Font getBaseFont() {
    Font font = getFont();
    if (font == null) font = UIUtil.getLabelFont();
    return font;
  }

  private TextLayout getTextLayout(@NotNull ColoredFragment fragment, Font font, FontRenderContext frc) {
    if (getBaseFont() != myLayoutFont) myFragments.forEach(each -> each.layout = null);
    TextLayout layout = fragment.layout;
    if (layout == null && needFontFallback(font, fragment.text)) {
      layout = createAndCacheTextLayout(fragment, font, frc);
    }
    return layout;
  }

  private void doDrawString(Graphics2D g, @NotNull ColoredFragment fragment, float x, float y) {
    String text = fragment.text;
    if (StringUtil.isEmpty(text)) return;
    TextLayout layout = getTextLayout(fragment, g.getFont(), g.getFontRenderContext());
    if (layout != null) {
      layout.draw(g, x, y);
    }
    else {
      g.drawString(text, x, y);
    }
  }

  private float computeStringWidth(@NotNull ColoredFragment fragment, Font font) {
    String text = fragment.text;
    if (StringUtil.isEmpty(text)) return 0;
    FontRenderContext fontRenderContext = getFontMetrics(font).getFontRenderContext();
    TextLayout layout = getTextLayout(fragment, font, fontRenderContext);
    return layout != null ? layout.getAdvance() : (float)font.getStringBounds(text, fontRenderContext).getWidth();
  }

  private TextLayout createAndCacheTextLayout(@NotNull ColoredFragment fragment, Font basefont, FontRenderContext fontRenderContext) {
    String text = fragment.text;
    AttributedString string = new AttributedString(text);
    int start = 0;
    int end = text.length();
    AttributedCharacterIterator it = string.getIterator(new AttributedCharacterIterator.Attribute[0], start, end);
    Font currentFont = basefont;
    int currentIndex = start;
    for(char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      Font font = basefont;
      if (!font.canDisplay(c)) {
        for (SuitableFontProvider provider : SuitableFontProvider.EP_NAME.getExtensions()) {
          font = provider.getFontAbleToDisplay(c, basefont.getSize(), basefont.getStyle(), basefont.getFamily());
          if (font != null) break;
        }
      }
      int i = it.getIndex();
      if (!Comparing.equal(currentFont, font)) {
        if (i > currentIndex) {
          string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, i);
        }
        currentFont = font;
        currentIndex = i;
      }
    }
    if (currentIndex < end) {
      string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, end);
    }
    TextLayout layout = new TextLayout(string.getIterator(), fontRenderContext);
    fragment.layout = layout;
    myLayoutFont = getBaseFont();
    return layout;
  }

  private static boolean needFontFallback(Font font, String text) {
    return font.canDisplayUpTo(text) != -1
           && text.indexOf(CharacterIterator.DONE) == -1; // see IDEA-137517, TextLayout does not support this character
  }

  /**
   * Returns the index of text fragment at the specified X offset.
   *
   * @param x the offset
   * @return the index of the fragment, {@link #FRAGMENT_ICON} if the icon is at the offset, or -1 if nothing is there.
   */
  public int findFragmentAt(int x) {
    float curX = myIpad.left;
    if (myIcon != null && !myIconOnTheRight) {
      final int iconRight = myIcon.getIconWidth() + myIconTextGap;
      if (x < iconRight) {
        return FRAGMENT_ICON;
      }
      curX += iconRight;
    }

    Font font = getBaseFont();

    int baseSize = font.getSize();
    boolean wasSmaller = false;
    synchronized (myFragments) {
      for (int i = 0; i < myFragments.size(); i++) {
        ColoredFragment fragment = myFragments.get(i);
        SimpleTextAttributes attributes = fragment.attributes;
        boolean isSmaller = attributes.isSmaller();
        if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
          font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
        }
        wasSmaller = isSmaller;

        final float curWidth = computeStringWidth(fragment, font);
        if (x >= curX && x < curX + curWidth) {
          return i;
        }
        curX += curWidth;
        final int fragmentPadding = fragment.padding;
        if (fragmentPadding > 0 && curX < fragmentPadding) {
          curX = fragmentPadding;
        }
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

  @Nullable
  public Object getFragmentTagAt(int x) {
    int index = findFragmentAt(x);
    return index < 0 ? null : getFragmentTag(index);
  }

  @NotNull
  protected JLabel formatToLabel(@NotNull JLabel label) {
    label.setIcon(myIcon);

    synchronized (myFragments) {
      if (!myFragments.isEmpty()) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"white-space:nowrap\">");

        for (ColoredFragment fragment : myFragments) {
          final String text = fragment.text;
          final SimpleTextAttributes attributes = fragment.attributes;
          final Object tag = fragment.tag;
          if (tag instanceof BrowserLauncherTag) {
            formatLink(sb, text, attributes, ((BrowserLauncherTag)tag).myUrl);
          }
          else {
            formatText(sb, text, attributes);
          }
        }

        sb.append("</body></html>");
        label.setText(sb.toString());
      }
    }

    return label;
  }

  static void formatText(@NotNull StringBuilder builder, @NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    if (!fragment.isEmpty()) {
      builder.append("<span");
      formatStyle(builder, attributes);
      builder.append('>').append(convertFragment(fragment)).append("</span>");
    }
  }

  static void formatLink(@NotNull StringBuilder builder,
                         @NotNull String fragment,
                         @NotNull SimpleTextAttributes attributes,
                         @NotNull String url) {
    if (!fragment.isEmpty()) {
      builder.append("<a href=\"").append(StringUtil.replace(url, "\"", "%22")).append("\"");
      formatStyle(builder, attributes);
      builder.append('>').append(convertFragment(fragment)).append("</a>");
    }
  }

  private static String convertFragment(@NotNull String fragment) {
    return StringUtil.escapeXmlEntities(fragment).replaceAll("\\\\n", "<br>");
  }

  private static void formatStyle(final StringBuilder builder, final SimpleTextAttributes attributes) {
    final Color fgColor = attributes.getFgColor();
    final Color bgColor = attributes.getBgColor();
    final int style = attributes.getStyle();

    final int pos = builder.length();
    if (fgColor != null) {
      builder.append("color:").append(ColorUtil.toHtmlColor(fgColor)).append(';');
    }
    if (bgColor != null) {
      builder.append("background-color:").append(ColorUtil.toHtmlColor(bgColor)).append(';');
    }
    if ((style & SimpleTextAttributes.STYLE_BOLD) != 0) {
      builder.append("font-weight:bold;");
    }
    if ((style & SimpleTextAttributes.STYLE_ITALIC) != 0) {
      builder.append("font-style:italic;");
    }
    if ((style & SimpleTextAttributes.STYLE_UNDERLINE) != 0) {
      builder.append("text-decoration:underline;");
    }
    else if ((style & SimpleTextAttributes.STYLE_STRIKEOUT) != 0) {
      builder.append("text-decoration:line-through;");
    }
    if (builder.length() > pos) {
      builder.insert(pos, " style=\"");
      builder.append('"');
    }
  }

  @Override
  protected void paintComponent(final Graphics g) {
    try {
      doPaint((Graphics2D)g);
    }
    catch (RuntimeException e) {
      LOG.error(logSwingPath(), e);
      throw e;
    }
  }

  protected void doPaint(final Graphics2D g) {
    synchronized (myFragments) {
      int offset = 0;
      final Icon icon = myIcon; // guard against concurrent modification (IDEADEV-12635)
      if (icon != null && !myIconOnTheRight) {
        doPaintIcon(g, icon, 0);
        offset += myIpad.left + icon.getIconWidth() + myIconTextGap;
      }

      doPaintTextBackground(g, offset);
      offset = doPaintText(g, offset, myFocusBorderAroundIcon || icon == null) + myIconTextGap;
      if (icon != null && myIconOnTheRight) {
        doPaintIcon(g, icon, offset);
      }
    }
  }

  private void doPaintTextBackground(Graphics2D g, int offset) {
    if (isOpaque() || shouldDrawBackground()) {
      paintBackground(g, offset, getWidth() - offset, getHeight());
    }
  }

  protected void paintBackground(Graphics2D g, int x, int width, int height) {
    g.setColor(getBackground());
    g.fillRect(x, 0, width, height);
  }

  protected void doPaintIcon(@NotNull Graphics2D g, @NotNull Icon icon, int offset) {
    final Container parent = getParent();
    Color iconBackgroundColor = null;
    if ((isOpaque() || isIconOpaque()) && !isTransparentIconBackground()) {
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

  protected int doPaintText(Graphics2D g, int textStart, boolean focusAroundIcon) {
    synchronized (myFragments) {
      // If there is no icon, then we have to add left internal padding
      if (textStart == 0) {
        textStart = myIpad.left;
      }

      float offset = textStart;
      if (myBorder != null) {
        offset += myBorder.getBorderInsets(this).left;
      }
      offset += getInsets().left;

      class Frag {
        private final int index;
        private final float start;
        private final float end;
        private final float baseLine;
        private final Font font;
        private final Frag next;

        private Frag(int index, float start, float end, float baseLine, @NotNull Font font, Frag next) {
          this.index = index;
          this.start = start;
          this.end = end;
          this.baseLine = baseLine;
          this.font = font;
          this.next = next;
        }
      }
      int height = getHeight();

      applyAdditionalHints(g);
      final Font baseFont = getBaseFont();
      g.setFont(baseFont);
      offset += computeTextAlignShift();
      int baseSize = baseFont.getSize();
      FontMetrics baseMetrics = g.getFontMetrics();
      Rectangle area = computePaintArea();
      final int textBaseline = area.y + getTextBaseLine(baseMetrics, area.height);
      boolean wasSmaller = false;
      Frag secondPassFrag = null;
      for (int i = 0; i < myFragments.size(); i++) {
        ColoredFragment fragment = myFragments.get(i);
        final SimpleTextAttributes attributes = fragment.attributes;

        Font font = g.getFont();
        boolean isSmaller = attributes.isSmaller();
        if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
          font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
        }
        wasSmaller = isSmaller;

        g.setFont(font);
        final FontMetrics metrics = g.getFontMetrics(font);

        final float fragmentWidth = computeStringWidth(fragment, font);

        final int fragmentPadding = fragment.padding;

        boolean secondPass = attributes.isSearchMatch() || attributes.isClickable();
        final Color bgColor = secondPass ? null : attributes.getBgColor();
        if ((attributes.isOpaque() || isOpaque()) && bgColor != null) {
          doPaintFragmentBackground(g, i, bgColor, (int)offset, 0, (int)fragmentWidth, height);
        }

        Color color = isEnabled() ? getActiveTextColor(attributes.getFgColor()) : UIUtil.getInactiveTextColor();
        g.setColor(color);

        final int fragmentAlignment = fragment.alignment;

        final float endOffset;
        if (fragmentPadding > 0 &&
            fragmentPadding > fragmentWidth) {
          endOffset = fragmentPadding;
          if (fragmentAlignment == SwingConstants.RIGHT || fragmentAlignment == SwingConstants.TRAILING) {
            offset = fragmentPadding - fragmentWidth;
          }
        }
        else {
          endOffset = offset + fragmentWidth;
        }

        if (!secondPass) {
          if (shouldDrawDimmed()) {
            color = ColorUtil.dimmer(color);
          }

          g.setColor(color);
          doDrawString(g, fragment, offset, textBaseline);

          // for some reason strokeState here may be incorrect, resetting the stroke helps
          g.setStroke(g.getStroke());

          drawTextAttributes(g, attributes, (int)offset, textBaseline, (int)fragmentWidth, metrics, font);
        }

        if (secondPass) {
          secondPassFrag = new Frag(i, offset, offset + fragmentWidth, textBaseline, font, secondPassFrag);
        }

        offset = endOffset;
      }

      // Paint focus border around the text and icon (if necessary)
      if (myPaintFocusBorder && myBorder != null) {
        int width = getWidth();
        if (focusAroundIcon) {
          myBorder.paintBorder(this, g, 0, 0, width, height);
        }
        else {
          myBorder.paintBorder(this, g, textStart, 0, width - textStart, height);
        }
      }

      // draw search matches after all
      for (Frag frag = secondPassFrag; frag != null; frag = frag.next) {
        float x1 = frag.start;
        float x2 = frag.end;
        float baseline = frag.baseLine;
        ColoredFragment fragment = myFragments.get(frag.index);
        String text = fragment.text;
        SimpleTextAttributes attributes = fragment.attributes;
        Color fgColor;
        if (attributes.isSearchMatch()) {
          fgColor = new JBColor(Gray._50, Gray._0);
          UIUtil.drawSearchMatch(g, x1, x2, height);
        }
        else if (attributes.isClickable()) {
          boolean selected = UIUtil.getTreeSelectionBackground(true) == getBackground();
          fgColor = ObjectUtils.notNull(attributes.getFgColor(), UIUtil.getLabelForeground());
          drawClickableFrag(g, x1, x2, height, selected, attributes.isHovered());
        }
        else {
          continue;
        }
        g.setFont(frag.font);
        g.setColor(fgColor);
        g.drawString(text, x1, baseline);

        int fragmentWidth = (int)(x2 - x1);
        drawTextAttributes(g, attributes, (int)x1, (int)baseline, fragmentWidth, g.getFontMetrics(), g.getFont());
      }
      return (int)offset;
    }
  }

  protected Color getActiveTextColor(Color attributesColor) {
    // in case if color is not defined we have to get foreground color from Swing hierarchy
    return attributesColor != null ? attributesColor : getForeground();
  }

  protected void doPaintFragmentBackground(@NotNull Graphics2D g, int index, @NotNull Color bgColor, int x, int y, int width, int height) {
    g.setColor(bgColor);
    g.fillRect(x, y, width, height);
  }

  private static void drawClickableFrag(Graphics2D g, float x1, float x2, int height, boolean selected, boolean hovered) {
    GraphicsConfig c = GraphicsUtil.setupRoundedBorderAntialiasing(g);
    RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x1 + 1, 2, x2 - x1 - 2, height - 4, 6, 6);
    g.setColor(JBUI.CurrentTheme.ActionButton.hoverBackground());
    if (hovered && !selected) {
      g.fill(shape);
    }
    else {
      g.draw(shape);
    }
    c.restore();
  }

  private void drawTextAttributes(@NotNull Graphics2D g,
                                  @NotNull SimpleTextAttributes attributes,
                                  int offset,
                                  int textBaseline,
                                  int fragmentWidth,
                                  @NotNull FontMetrics metrics,
                                  Font font) {
    if (attributes.isStrikeout()) {
      EffectPainter.STRIKE_THROUGH.paint(g, offset, textBaseline, fragmentWidth, getCharHeight(g), font);
    }

    if (attributes.isWaved()) {
      if (attributes.getWaveColor() != null) {
        g.setColor(attributes.getWaveColor());
      }
      EffectPainter.WAVE_UNDERSCORE.paint(g, offset, textBaseline + 1, fragmentWidth, Math.max(2, metrics.getDescent()), font);
    }

    if (attributes.isUnderline()) {
      EffectPainter.LINE_UNDERSCORE.paint(g, offset, textBaseline, fragmentWidth, metrics.getDescent(), font);
    }

    if (attributes.isBoldDottedLine()) {
      final int dottedAt = SystemInfo.isMac ? textBaseline : textBaseline + 1;
      final Color lineColor = attributes.getWaveColor();
      UIUtil.drawBoldDottedLine(g, offset, offset + fragmentWidth, dottedAt, attributes.getBgColor(), lineColor, isOpaque());
    }
  }

  private static int getCharHeight(Graphics g) {
    // magic of determining character height
    return g.getFontMetrics().charWidth('a');
  }

  private int computeTextAlignShift() {
    if (myTextAlign == SwingConstants.LEFT || myTextAlign == SwingConstants.LEADING) {
      return 0;
    }

    int componentWidth = getSize().width;
    int excessiveWidth = componentWidth - computePreferredSize(false).width;
    if (excessiveWidth <= 0) {
      return 0;
    }

    if (myTextAlign == SwingConstants.CENTER) {
      return excessiveWidth / 2;
    }
    if (myTextAlign == SwingConstants.RIGHT || myTextAlign == SwingConstants.TRAILING) {
      return excessiveWidth;
    }
    return 0;
  }

  /**
   * @deprecated and won't be used anymore
   */
  @Deprecated
  protected boolean shouldDrawMacShadow() {
    return false;
  }

  protected boolean shouldDrawDimmed() {
    return false;
  }

  protected boolean shouldDrawBackground() {
    return false;
  }

  protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon, int offset) {
    Rectangle area = computePaintArea();
    icon.paintIcon(this, g, offset, area.y + (area.height - icon.getIconHeight() + 1) / 2);
  }

  protected void applyAdditionalHints(@NotNull Graphics2D g) {
    UISettings.setupAntialiasing(g);
  }

  @Override
  public int getBaseline(int width, int height) {
    super.getBaseline(width, height);
    return getTextBaseLine(getFontMetrics(getFont()), height);
  }

  public boolean isTransparentIconBackground() {
    return myTransparentIconBackground;
  }

  public void setTransparentIconBackground(boolean transparentIconBackground) {
    myTransparentIconBackground = transparentIconBackground;
  }

  public static int getTextBaseLine(@NotNull FontMetrics metrics, final int height) {
    // adding leading to ascent, just like in editor (leads to bad presentation for certain fonts with Oracle JDK, see IDEA-167541)
    return (height - metrics.getHeight()) / 2 + metrics.getAscent() +
           (SystemInfo.isJetBrainsJvm ? metrics.getLeading() : 0);
  }

  @NotNull
  private String logSwingPath() {
    //noinspection HardCodedStringLiteral
    final StringBuilder buffer = new StringBuilder("Components hierarchy:\n");
    for (Container c = this; c != null; c = c.getParent()) {
      buffer.append('\n');
      buffer.append(c);
    }
    return buffer.toString();
  }

  protected void setBorderInsets(Insets insets) {
    myBorder = new JBEmptyBorder(insets);
    revalidateAndRepaint();
  }

  @NotNull
  public CharSequence getCharSequence(boolean mainOnly) {
    synchronized (myFragments) {
      int count = myFragments.size();
      if (count == 0) return "";
      if (mainOnly && myMainTextLastIndex > -1 && myMainTextLastIndex + 1 < myFragments.size()) {
        count = myMainTextLastIndex + 1;
      }
      StringBuilder sb = new StringBuilder(myFragments.get(0).text);
      for (int i = 1; i < count; i++) {
        sb.append(myFragments.get(i).text);
      }
      return sb.toString();
    }
  }

  @Override
  public String toString() {
    return getCharSequence(false).toString();
  }

  public void change(@NotNull Runnable runnable, boolean autoInvalidate) {
    boolean old = myAutoInvalidate;
    myAutoInvalidate = autoInvalidate;
    try {
      runnable.run();
    }
    finally {
      myAutoInvalidate = old;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleSimpleColoredComponent();
    }
    return accessibleContext;
  }

  protected class AccessibleSimpleColoredComponent extends JComponent.AccessibleJComponent {
    @Override
    public String getAccessibleName() {
      return getCharSequence(false).toString();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }
  }

  public static class BrowserLauncherTag implements Runnable {
    private final String myUrl;

    public BrowserLauncherTag(@NotNull String url) {
      myUrl = url;
    }

    @Override
    public void run() {
      BrowserUtil.browse(myUrl);
    }
  }

  public interface ColoredIterator extends Iterator<String> {
    int getOffset();

    int getEndOffset();

    @NotNull
    String getFragment();

    @NotNull
    SimpleTextAttributes getTextAttributes();

    @Nullable
    Object getTag();

    int split(int offset, @NotNull SimpleTextAttributes attributes);

    void setFragment(@NotNull String text);

    void setTag(@Nullable Object tag);

    void setTextAttributes(@NotNull SimpleTextAttributes attributes);
  }

  private class MyIterator implements ColoredIterator {
    int myIndex = -1;
    int myOffset;
    int myEndOffset;

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public int getEndOffset() {
      return myEndOffset;
    }

    @NotNull
    @Override
    public String getFragment() {
      synchronized (myFragments) {
        return myFragments.get(myIndex).text;
      }
    }

    @NotNull
    @Override
    public SimpleTextAttributes getTextAttributes() {
      synchronized (myFragments) {
        return myFragments.get(myIndex).attributes;
      }
    }

    @Nullable
    @Override
    public Object getTag() {
      synchronized (myFragments) {
        return myFragments.get(myIndex).tag;
      }
    }

    @Override
    public int split(int offset, @NotNull SimpleTextAttributes attributes) {
      synchronized (myFragments) {
        if (offset < 0 || offset > myEndOffset - myOffset) {
          throw new IllegalArgumentException(offset + " is not within [0, " + (myEndOffset - myOffset) + "]");
        }
        ColoredFragment oldFragment = myFragments.get(myIndex);
        if (offset == myEndOffset - myOffset) {   // replace
          oldFragment.attributes = attributes;
        }
        else if (offset > 0) {   // split
          String text = getFragment();
          ColoredFragment newFragment = new ColoredFragment(text.substring(offset), oldFragment.attributes);
          oldFragment.text = text.substring(0, offset);
          oldFragment.attributes = attributes;
          newFragment.tag = oldFragment;
          myFragments.add(myIndex + 1, newFragment);
          myIndex++;
        }
        myOffset += offset;
        return myOffset;
      }
    }
    @Override
    public boolean hasNext() {
      synchronized (myFragments) {
        return myIndex + 1 < myFragments.size();
      }
    }

    @Override
    public String next() {
      myIndex++;
      myOffset = myEndOffset;
      String text = getFragment();
      myEndOffset += text.length();
      return text;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setFragment(@NotNull String text) {
      synchronized (myFragments) {
        myFragments.get(myIndex).text = text;
      }
    }

    @Override
    public void setTag(@Nullable Object tag) {
      synchronized (myFragments) {
        myFragments.get(myIndex).tag = tag;
      }
    }

    @Override
    public void setTextAttributes(@NotNull SimpleTextAttributes attributes) {
      synchronized (myFragments) {
        myFragments.get(myIndex).attributes = attributes;
      }
    }
  }

  private static class ColoredFragment {
    @NotNull volatile String text;
    @NotNull volatile SimpleTextAttributes attributes;
    @Nullable volatile Object tag;
    @Nullable volatile TextLayout layout;
    volatile int padding;
    volatile int alignment;

    ColoredFragment(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
      this.text = text;
      this.attributes = attributes;
    }
  }
}
