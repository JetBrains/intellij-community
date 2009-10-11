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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.PopupIcons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LookupCellRenderer implements ListCellRenderer {
  private Icon myEmptyIcon = new EmptyIcon(5);
  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;
  private final int myFontWidth;

  public static final Color BACKGROUND_COLOR = new Color(235, 244, 254);
  private static final Color FOREGROUND_COLOR = Color.black;
  private static final Color GRAYED_FOREGROUND_COLOR = new Color(160, 160, 160);
  private static final Color SELECTED_BACKGROUND_COLOR = new Color(0, 82, 164);
  private static final Color SELECTED_FOREGROUND_COLOR = Color.white;
  private static final Color SELECTED_GRAYED_FOREGROUND_COLOR = Color.white;

  private static final Color PREFIX_FOREGROUND_COLOR = new Color(176, 0, 176);
  private static final Color SELECTED_PREFIX_FOREGROUND_COLOR = new Color(249, 236, 204);

  private static final Color EMPTY_ITEM_FOREGROUND_COLOR = FOREGROUND_COLOR;

  private static final int MAX_LENGTH = 70;

  private final LookupImpl myLookup;

  private final SimpleColoredComponent myNameComponent;
  private final SimpleColoredComponent myTailComponent;
  private final JLabel myTypeLabel;
  private final JLabel myArrowLabel; // actions' substep
  private final JPanel myPanel;

  public static final Color PREFERRED_BACKGROUND_COLOR = new Color(220, 245, 220);

  public LookupCellRenderer(LookupImpl lookup) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NORMAL_FONT = scheme.getFont(EditorFontType.PLAIN);
    BOLD_FONT = scheme.getFont(EditorFontType.BOLD);

    myLookup = lookup;
    myNameComponent = new MySimpleColoredComponent();
    myNameComponent.setIpad(new Insets(0, 0, 0, 0));

    myTailComponent = new MySimpleColoredComponent();
    myTailComponent.setIpad(new Insets(0, 0, 0, 0));
    myTailComponent.setFont(NORMAL_FONT);

    myTypeLabel = new JLabel("", SwingConstants.LEFT);
    myTypeLabel.setOpaque(true);
    myTypeLabel.setHorizontalTextPosition(SwingConstants.RIGHT);
    myTypeLabel.setFont(NORMAL_FONT);

    myArrowLabel = new JLabel("");
    myArrowLabel.setOpaque(true);

    myPanel = new LookupPanel();
    myPanel.add(myNameComponent, BorderLayout.WEST);
    myPanel.add(myTailComponent, BorderLayout.CENTER);
    myTailComponent.setBorder(new EmptyBorder(0, 0, 0, 10));

    LookupPanel eastPanel = new LookupPanel();
    eastPanel.add(myTypeLabel, BorderLayout.WEST);
    eastPanel.add(myArrowLabel, BorderLayout.EAST);
    myPanel.add(eastPanel, BorderLayout.EAST);

    myFontWidth = myLookup.getEditor().getComponent().getFontMetrics(BOLD_FONT).charWidth('W');

    UIUtil.removeQuaquaVisualMarginsIn(myPanel);
  }

  public Component getListCellRendererComponent(
      final JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean hasFocus) {

    final LookupElement item = (LookupElement)value;
    final Color foreground = isSelected ? SELECTED_FOREGROUND_COLOR : FOREGROUND_COLOR;
    final Color background = getItemBackground(list, index, isSelected);

    final LookupElementPresentation presentation = new LookupElementPresentation(true);
    item.renderElement(presentation);

    myNameComponent.clear();
    myNameComponent.setIcon(getIcon(presentation.getIcon()));
    myNameComponent.setBackground(background);
    setItemTextLabel(item, foreground, isSelected, presentation.getItemText(), presentation.isStrikeout(), presentation.isItemTextBold());

    myTypeLabel.setText(null);
    myTypeLabel.setBackground(background);
    setTypeTextLabel(item, background, foreground, presentation.getTypeText(), presentation.getTypeIcon());

    myTailComponent.setBackground(background);
    setTailTextLabel(isSelected, presentation, foreground);

    myArrowLabel.setIcon(myLookup.getActionsFor(item).isEmpty() ? PopupIcons.EMPTY_ICON : PopupIcons.HAS_NEXT_ICON_GRAYED);
    myArrowLabel.setBackground(background);
    myArrowLabel.setForeground(foreground);

    return myPanel;
  }

  private Color getItemBackground(JList list, int index, boolean isSelected) {
    final int preferredCount = myLookup.getPreferredItemsCount();
    if (index <= preferredCount - 1 && preferredCount < list.getModel().getSize() - 1) {
      return isSelected ? SELECTED_BACKGROUND_COLOR : PREFERRED_BACKGROUND_COLOR;
    }
    return isSelected ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR;
  }

  private void setTailTextLabel(boolean isSelected, LookupElementPresentation presentation, Color foreground) {
    final Color fg = getTailTextColor(isSelected, presentation, foreground);

    myTailComponent.clear();

    int style = SimpleTextAttributes.STYLE_PLAIN;
    if (presentation.isStrikeout()) {
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    }

    myTailComponent.append(StringUtil.notNullize(presentation.getTailText()), new SimpleTextAttributes(style, fg));
  }

  private static Color getTailTextColor(boolean isSelected, LookupElementPresentation presentation, Color defaultForeground) {
    if (presentation.isTailGrayed()) {
      return isSelected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR;
    }

    final Color tailForeground = presentation.getTailForeground();
    if (tailForeground != null) {
      return tailForeground;
    }

    return defaultForeground;
  }

  private void setItemTextLabel(LookupElement item, final Color foreground, final boolean selected, final String name, final boolean toStrikeout,
                                boolean bold) {
    myNameComponent.setFont(bold ? BOLD_FONT : NORMAL_FONT);

    int style = bold ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN;
    if (toStrikeout) {
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    }

    final String prefix = item.getPrefixMatcher().getPrefix();
    if (prefix.length() > 0 && StringUtil.startsWithIgnoreCase(name, prefix)){
      myNameComponent.append(name.substring(0, prefix.length()), new SimpleTextAttributes(style, selected ? SELECTED_PREFIX_FOREGROUND_COLOR : PREFIX_FOREGROUND_COLOR));
      myNameComponent.append(name.substring(prefix.length()), new SimpleTextAttributes(style, foreground));
    }
    else{
      myNameComponent.append(name, new SimpleTextAttributes(style, foreground));
    }
  }

  private void setTypeTextLabel(LookupElement item, final Color background, Color foreground, final String typeText, final Icon icon){
    myTypeLabel.setIcon(icon);

    JLabel label = myTypeLabel;

    label.setText(StringUtil.notNullize(typeText));

    Color sampleBackground = background;

    Object o = item.getObject();
    if (o instanceof LookupValueWithUIHint && label.getText().length() == 0) {
      Color proposedBackground = ((LookupValueWithUIHint)o).getColorHint();

      if (proposedBackground == null) {
        proposedBackground = background;
      }

      sampleBackground = proposedBackground;
      label.setText("  ");
    } else {
      label.setText("   " + label.getText());
    }

    if (item instanceof EmptyLookupItem) {
      foreground = EMPTY_ITEM_FOREGROUND_COLOR;
    }
    label.setBackground(sampleBackground);
    label.setForeground(foreground);
  }

  private Icon getIcon(Icon icon){
    if (icon == null) {
      return myEmptyIcon;
    }

    if (icon.getIconWidth() < myEmptyIcon.getIconWidth()) {
      final RowIcon rowIcon = new RowIcon(2);
      rowIcon.setIcon(icon, 0);
      rowIcon.setIcon(new EmptyIcon(myEmptyIcon.getIconWidth() - icon.getIconWidth()), 1);
      return rowIcon;
    }

    return icon;
  }

  public int updateMaximumWidth(final LookupElement item){
    final LookupElementPresentation p = new LookupElementPresentation(false);
    item.renderElement(p);
    final Icon icon = p.getIcon();
    if (icon != null && icon.getIconWidth() > myEmptyIcon.getIconWidth()) {
      myEmptyIcon = new EmptyIcon(icon.getIconWidth(), 2);
    }

    int maxWidth = Math.min(calculateWidth(p), MAX_LENGTH * myFontWidth);
    return maxWidth + myEmptyIcon.getIconWidth() + myNameComponent.getIconTextGap() + myFontWidth;
  }

  public int getIconIndent() {
    return myNameComponent.getIconTextGap() + myEmptyIcon.getIconWidth();
  }

  private int calculateWidth(LookupElementPresentation presentation) {
    int result = 2;
    result += getStringWidth(presentation.getItemText());
    result += getStringWidth(presentation.getTailText());
    result += getStringWidth("XXXX"); //3 spaces for nice tail-type separation, one for unforeseen Swing size adjustments 
    result += getStringWidth(presentation.getTypeText());
    final Icon typeIcon = presentation.getTypeIcon();
    if (typeIcon != null) {
      result += 2;
      result += typeIcon.getIconWidth();
    }
    return result;
  }

  private int getStringWidth(@Nullable final String text) {
    if (text != null) {
      return text.length() * myFontWidth;
    }
    return 0;
  }


  private static class MySimpleColoredComponent extends SimpleColoredComponent {
    private MySimpleColoredComponent() {
      setFocusBorderAroundIcon(true);
      setBorderInsets(new Insets(0, 0, 0, 0));
    }
  }

  private static class LookupPanel extends JPanel {
    public LookupPanel() {
      super(new BorderLayout());
    }

    public void paint(Graphics g){
      UISettings.setupAntialiasing(g);
      super.paint(g);
    }
  }
}
