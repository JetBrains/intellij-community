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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.codeInsight.lookup.RealLookupElementPresentation;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 * @author Konstantin Bulenkov
 */
public class LookupCellRenderer implements ListCellRenderer {
  //TODO[kb]: move all these awesome constants to Editor's Fonts & Colors settings
  private static final int AFTER_TAIL = 10;
  private static final int AFTER_TYPE = 6;
  public static final Color BACKGROUND_COLOR_DARK_VARIANT = new Color(0x141D29);
  private Icon myEmptyIcon = EmptyIcon.create(5);
  private final Font myNormalFont;
  private final Font myBoldFont;
  private final FontMetrics myNormalMetrics;
  private final FontMetrics myBoldMetrics;

  public static final Color BACKGROUND_COLOR = new Color(235, 244, 254);
  private static final Color FOREGROUND_COLOR = Color.black;
  private static final Color GRAYED_FOREGROUND_COLOR = Gray._160;
  private static final Color SELECTED_BACKGROUND_COLOR = new Color(0, 82, 164);
  private static final Color SELECTED_FOREGROUND_COLOR = Color.white;
  private static final Color SELECTED_GRAYED_FOREGROUND_COLOR = Color.white;

  static final Color PREFIX_FOREGROUND_COLOR = new Color(176, 0, 176);
  private static final Color SELECTED_PREFIX_FOREGROUND_COLOR = new Color(249, 236, 204);

  private static final Color EMPTY_ITEM_FOREGROUND_COLOR = FOREGROUND_COLOR;

  private final LookupImpl myLookup;

  private final SimpleColoredComponent myNameComponent;
  private final SimpleColoredComponent myTailComponent;
  private final SimpleColoredComponent myTypeLabel;
  private final LookupPanel myPanel;
  private final Map<Integer, Boolean> mySelected = new HashMap<Integer, Boolean>();

  private static final String ELLIPSIS = "\u2026";
  private int myMaxWidth = -1;

  public LookupCellRenderer(LookupImpl lookup) {
    EditorColorsScheme scheme = lookup.getEditor().getColorsScheme();
    myNormalFont = scheme.getFont(EditorFontType.PLAIN);
    myBoldFont = scheme.getFont(EditorFontType.BOLD);

    myLookup = lookup;
    myNameComponent = new MySimpleColoredComponent();
    myNameComponent.setIpad(new Insets(0, 0, 0, 0));

    myTailComponent = new MySimpleColoredComponent();
    myTailComponent.setIpad(new Insets(0, 0, 0, 0));
    myTailComponent.setFont(myNormalFont);

    myTypeLabel = new MySimpleColoredComponent();
    myTypeLabel.setIpad(new Insets(0, 0, 0, 0));
    myTypeLabel.setFont(myNormalFont);

    myPanel = new LookupPanel();
    myPanel.add(myNameComponent, BorderLayout.WEST);
    myPanel.add(myTailComponent, BorderLayout.CENTER);
    myTailComponent.setBorder(new EmptyBorder(0, 0, 0, AFTER_TAIL));

    myPanel.add(myTypeLabel, BorderLayout.EAST);
    myTypeLabel.setBorder(new EmptyBorder(0, 0, 0, AFTER_TYPE));

    myNormalMetrics = myLookup.getEditor().getComponent().getFontMetrics(myNormalFont);
    myBoldMetrics = myLookup.getEditor().getComponent().getFontMetrics(myBoldFont);
  }

  private boolean myIsSelected = false;
  @Override
  public Component getListCellRendererComponent(
      final JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean hasFocus) {


    if (!myLookup.isFocused()) {
      isSelected = false;
    }

    myIsSelected = isSelected;
    final LookupElement item = (LookupElement)value;
    final Color foreground = getForegroundColor(isSelected);
    final Color background = isSelected ? SELECTED_BACKGROUND_COLOR : new JBColor(BACKGROUND_COLOR, BACKGROUND_COLOR_DARK_VARIANT);

    int allowedWidth = list.getWidth() - AFTER_TAIL - AFTER_TYPE - getIconIndent();
    final LookupElementPresentation presentation = new RealLookupElementPresentation(isSelected ? getMaxWidth() : allowedWidth, myNormalMetrics, myBoldMetrics, myLookup);
    if (item.isValid()) {
      item.renderElement(presentation);
    } else {
      presentation.setItemTextForeground(JBColor.RED);
      presentation.setItemText("Invalid");
    }

    myNameComponent.clear();
    myNameComponent.setIcon(augmentIcon(presentation.getIcon(), myEmptyIcon));
    myNameComponent.setBackground(background);
    allowedWidth -= setItemTextLabel(item, new JBColor(isSelected ? SELECTED_FOREGROUND_COLOR : presentation.getItemTextForeground(), foreground), isSelected, presentation, allowedWidth);

    myTypeLabel.clear();
    if (allowedWidth > 0) {
      allowedWidth -= setTypeTextLabel(item, background, foreground, presentation, isSelected ? getMaxWidth() : allowedWidth, isSelected);
    }

    myTailComponent.clear();
    myTailComponent.setBackground(background);
    if (isSelected || allowedWidth >= 0) {
      setTailTextLabel(isSelected, presentation, foreground, isSelected ? getMaxWidth() : allowedWidth);
    }

    if (mySelected.containsKey(index)) {
      if (!isSelected && mySelected.get(index)) {
        myPanel.setUpdateExtender(true);
      }
    }
    mySelected.put(index, isSelected);

    final double w = myNameComponent.getPreferredSize().getWidth() +
                     myTailComponent.getPreferredSize().getWidth() +
                     myTypeLabel.getPreferredSize().getWidth();

    myPanel.removeAll();
    if (isSelected && w > list.getWidth()) {
      myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.X_AXIS));
      myPanel.add(myNameComponent);
      myPanel.add(myTailComponent);
      myPanel.add(myTypeLabel);
    } else {
      myPanel.setLayout(new BorderLayout());
      myPanel.add(myNameComponent, BorderLayout.WEST);
      myPanel.add(myTailComponent, BorderLayout.CENTER);
      myPanel.add(myTypeLabel, BorderLayout.EAST);
    }

    return myPanel;
  }

  private static Color getForegroundColor(boolean isSelected) {
    return UIUtil.isUnderDarcula() ? UIUtil.getListForeground() : isSelected ? SELECTED_FOREGROUND_COLOR : FOREGROUND_COLOR;
  }

  private int getMaxWidth() {
    if (myMaxWidth < 0) {
      final Point p = myLookup.getComponent().getLocationOnScreen();
      final Rectangle rectangle = ScreenUtil.getScreenRectangle(p);
      myMaxWidth = rectangle.x + rectangle.width - p.x - 111;
    }
    return myMaxWidth;
  }

  private void setTailTextLabel(boolean isSelected, LookupElementPresentation presentation, Color foreground, int allowedWidth) {
    int style = getStyle(false, presentation.isStrikeout(), false);

    for (LookupElementPresentation.TextFragment fragment : presentation.getTailFragments()) {
      if (allowedWidth < 0) {
        return;
      }

      String trimmed = trimLabelText(fragment.text, allowedWidth, myNormalMetrics);
      myTailComponent.append(trimmed, new SimpleTextAttributes(style, getTailTextColor(isSelected, fragment, foreground)));
      allowedWidth -= RealLookupElementPresentation.getStringWidth(trimmed, myNormalMetrics);
    }
  }

  private String trimLabelText(@Nullable String text, int maxWidth, FontMetrics metrics) {
    if (text == null || StringUtil.isEmpty(text)) {
      return "";
    }

    final int strWidth = RealLookupElementPresentation.getStringWidth(text, metrics);
    if (strWidth <= maxWidth || myIsSelected) {
      return text;
    }

    if (RealLookupElementPresentation.getStringWidth(ELLIPSIS, metrics) > maxWidth) {
      return "";
    }

    int i = 0;
    int j = text.length();
    while (i + 1 < j) {
      int mid = (i + j) / 2;
      final String candidate = text.substring(0, mid) + ELLIPSIS;
      final int width = RealLookupElementPresentation.getStringWidth(candidate, metrics);
      if (width <= maxWidth) {
        i = mid;
      } else {
        j = mid;
      }
    }

    return text.substring(0, i) + ELLIPSIS;
  }

  public static Color getTailTextColor(boolean isSelected, LookupElementPresentation.TextFragment fragment, Color defaultForeground) {
    if (fragment.isGrayed()) {
      return getGrayedForeground(isSelected);
    }

    if (!isSelected) {
      final Color tailForeground = fragment.getForegroundColor();
      if (tailForeground != null) {
        return tailForeground;
      }
    }

    return defaultForeground;
  }

  public static Color getGrayedForeground(boolean isSelected) {
    return UIUtil.isUnderDarcula() ? Gray._230 : isSelected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR;
  }

  private int setItemTextLabel(LookupElement item, final Color foreground, final boolean selected, LookupElementPresentation presentation, int allowedWidth) {
    boolean bold = presentation.isItemTextBold();

    myNameComponent.setFont(bold ? myBoldFont : myNormalFont);

    int style = getStyle(bold, presentation.isStrikeout(), presentation.isItemTextUnderlined());

    final FontMetrics metrics = bold ? myBoldMetrics : myNormalMetrics;
    final String name = trimLabelText(presentation.getItemText(), allowedWidth, metrics);
    int used = RealLookupElementPresentation.getStringWidth(name, metrics);

    renderItemName(item, foreground, selected, style, name, myNameComponent);
    return used;
  }

  @SimpleTextAttributes.StyleAttributeConstant
  private static int getStyle(boolean bold, boolean strikeout, boolean underlined) {
    int style = bold ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN;
    if (strikeout) {
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    }
    if (underlined) {
      style |= SimpleTextAttributes.STYLE_UNDERLINE;
    }
    return style;
  }

  private void renderItemName(LookupElement item,
                      Color foreground,
                      boolean selected,
                      @SimpleTextAttributes.StyleAttributeConstant int style,
                      String name,
                      final SimpleColoredComponent nameComponent) {
    final SimpleTextAttributes base = new SimpleTextAttributes(style, foreground);

    final String prefix = item instanceof EmptyLookupItem ? "" : myLookup.itemPattern(item);
    if (prefix.length() > 0) {
      Iterable<TextRange> ranges = getMatchingFragments(prefix, name);
      if (ranges != null) {
        SimpleTextAttributes highlighted =
          new SimpleTextAttributes(style, selected ? SELECTED_PREFIX_FOREGROUND_COLOR : PREFIX_FOREGROUND_COLOR);
        SpeedSearchUtil.appendColoredFragments(nameComponent, name, ranges, base, highlighted);
        return;
      }
    }
    nameComponent.append(name, base);
  }

  public static FList<TextRange> getMatchingFragments(String prefix, String name) {
    return new MinusculeMatcher("*" + prefix, NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(name);
  }

  private int setTypeTextLabel(LookupElement item,
                               final Color background,
                               Color foreground,
                               final LookupElementPresentation presentation,
                               int allowedWidth,
                               boolean selected) {
    final String givenText = presentation.getTypeText();
    final String labelText = trimLabelText(StringUtil.isEmpty(givenText) ? "" : " " + givenText, allowedWidth, myNormalMetrics);

    int used = RealLookupElementPresentation.getStringWidth(labelText, myNormalMetrics);

    final Icon icon = presentation.getTypeIcon();
    if (icon != null) {
      myTypeLabel.setIcon(icon);
      used += icon.getIconWidth();
    }

    Color sampleBackground = background;

    Object o = item.getObject();
    //noinspection deprecation
    if (o instanceof LookupValueWithUIHint && StringUtil.isEmpty(labelText)) {
      //noinspection deprecation
      Color proposedBackground = ((LookupValueWithUIHint)o).getColorHint();
      if (proposedBackground != null) {
        sampleBackground = proposedBackground;
      }
      myTypeLabel.append("  ");
      used += myNormalMetrics.stringWidth("WW");
    } else {
      myTypeLabel.append(labelText);
    }

    myTypeLabel.setBackground(sampleBackground);
    myTypeLabel.setForeground(presentation.isTypeGrayed() ? getGrayedForeground(selected) : item instanceof EmptyLookupItem ? UIUtil.isUnderDarcula() ? Gray._230 : EMPTY_ITEM_FOREGROUND_COLOR : foreground);
    return used;
  }

  public static Icon augmentIcon(@Nullable Icon icon, @NotNull Icon standard) {
    if (icon == null) {
      return standard;
    }

    if (icon.getIconHeight() < standard.getIconHeight() || icon.getIconWidth() < standard.getIconWidth()) {
      final LayeredIcon layeredIcon = new LayeredIcon(2);
      layeredIcon.setIcon(icon, 0, 0, (standard.getIconHeight() - icon.getIconHeight()) / 2);
      layeredIcon.setIcon(standard, 1);
      return layeredIcon;
    }

    return icon;
  }

  public int updateMaximumWidth(final LookupElementPresentation p) {
    final Icon icon = p.getIcon();
    if (icon != null && (icon.getIconWidth() > myEmptyIcon.getIconWidth() || icon.getIconHeight() > myEmptyIcon.getIconHeight())) {
      myEmptyIcon = new EmptyIcon(Math.max(icon.getIconWidth(), myEmptyIcon.getIconWidth()), Math.max(icon.getIconHeight(), myEmptyIcon.getIconHeight()));
    }

    return RealLookupElementPresentation.calculateWidth(p, myNormalMetrics, myBoldMetrics) + AFTER_TAIL + AFTER_TYPE;
  }

  public int getIconIndent() {
    return myNameComponent.getIconTextGap() + myEmptyIcon.getIconWidth();
  }


  private static class MySimpleColoredComponent extends SimpleColoredComponent {
    private MySimpleColoredComponent() {
      setFocusBorderAroundIcon(true);
    }

    @Override
    protected void applyAdditionalHints(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
    }
  }

  private class LookupPanel extends JPanel {
    boolean myUpdateExtender;
    public LookupPanel() {
      super(new BorderLayout());
    }

    public void setUpdateExtender(boolean updateExtender) {
      myUpdateExtender = updateExtender;
    }

    @Override
    public void paint(Graphics g){
      if (!myLookup.isFocused() && myLookup.isCompletion()) {
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
      }
      super.paint(g);
      //if (myUpdateExtender) {
      //  myLookup.updateExtender();
      //}
    }
  }
}
