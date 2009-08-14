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

  private final LookupElementPresentationImpl myLookupElementPresentation = new LookupElementPresentationImpl();
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
    myTypeLabel = new JLabel("", SwingConstants.LEFT);
    myTypeLabel.setOpaque(true);
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
    Color background = isSelected ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR;
    final Color foreground = isSelected ? SELECTED_FOREGROUND_COLOR : FOREGROUND_COLOR;
    final int preferredCount = myLookup.getPreferredItemsCount();
    if (index <= preferredCount - 1 && preferredCount < list.getModel().getSize() - 1) {
      background = isSelected ? SELECTED_BACKGROUND_COLOR : PREFERRED_BACKGROUND_COLOR;
    }

    myLookupElementPresentation.setContext(item, background, foreground, list, isSelected);

    myNameComponent.clear();
    myTailComponent.clear();
    myTypeLabel.setText(null);
    myArrowLabel.setIcon(myLookup.getActionsFor(item).isEmpty() ? PopupIcons.EMPTY_ICON : PopupIcons.HAS_NEXT_ICON_GRAYED);
    myArrowLabel.setBackground(background);
    myArrowLabel.setForeground(foreground);

    myNameComponent.setIcon(myEmptyIcon);
    item.renderElement(myLookupElementPresentation);

    return myPanel;
  }

  private void setItemTextLabels(LookupElement item, final Color background, final Color foreground, final boolean selected, final String name,
                                 final boolean toStrikeout, boolean bold) {
    final Icon icon = myNameComponent.getIcon();
    myNameComponent.clear();
    myNameComponent.setIcon(icon);
    myNameComponent.setFont(bold ? BOLD_FONT : NORMAL_FONT);
    myNameComponent.setBackground(background);

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

  private void setTailTextLabel(final Color background, Color foreground, String text, final boolean bold, final boolean strikeout) {
    if (text == null) {
      text = "";
    }

    myTailComponent.clear();

    myTailComponent.setFont(bold ? BOLD_FONT : NORMAL_FONT);
    myTailComponent.setBackground(background);

    int style = bold ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN;
    if (strikeout) {
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    }

    myTailComponent.append(text, new SimpleTextAttributes(style, foreground));
  }

  private void setTypeTextLabel(LookupElement item, final Color background, Color foreground, JList list, final String text3, final Icon icon){
    myTypeLabel.setHorizontalTextPosition(SwingConstants.RIGHT);
    myTypeLabel.setIcon(icon);

    String text = text3;

    final int listWidth = Math.max(list.getFixedCellWidth(), list.getWidth());
    final int maxWidth = listWidth - myNameComponent.getPreferredSize().width - 3 * myFontWidth;

    JLabel label = myTypeLabel;
    if (text == null) text = "";

    label.setText(text);
    label.setFont(NORMAL_FONT);

    if (maxWidth > 0 && label.getPreferredSize().width > maxWidth) {
      label.setText("...");
    }

    Color sampleBackground = background;

    Object o = item.getObject();
    if (o instanceof LookupValueWithUIHint && label.getText().length() == 0) {
      Color proposedBackground = ((LookupValueWithUIHint)o).getColorHint();

      if (proposedBackground == null) {
        proposedBackground = background;
      }

      sampleBackground = proposedBackground;
      label.setText("  ");
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
    WidthCalculatingPresentation p = new WidthCalculatingPresentation();
    item.renderElement(p);
    int maxWidth = p.myTotalWidth;
    if (p.myIconWidth > myEmptyIcon.getIconWidth()) {
      myEmptyIcon = new EmptyIcon(p.myIconWidth, 2);
    }
    maxWidth = Math.min(maxWidth, MAX_LENGTH * myFontWidth);
    return maxWidth + myEmptyIcon.getIconWidth() + myNameComponent.getIconTextGap() + myFontWidth;
  }

  public int getIconIndent() {
    return myNameComponent.getIconTextGap() + myEmptyIcon.getIconWidth();
  }

  private class LookupElementPresentationImpl extends LookupElementPresentation {
    private LookupElement myItem;
    private Color myBackground;
    private Color myForeground;
    private JList myList;
    private boolean mySelected;

    public boolean isSelected() {
      return mySelected;
    }

    public void setIcon(final Icon icon) {
      myNameComponent.setIcon(getIcon(icon));
    }

    public void setItemText(@Nullable final String text, final boolean strikeout, final boolean bold) {
      setItemTextLabels(myItem, myBackground, myForeground, mySelected, text, strikeout, bold);
    }

    public void setTailText(@Nullable final String text, final boolean grayed, final boolean bold, final boolean strikeout) {
      final Color foreground = grayed
                               ? mySelected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR
                               : null;
      setTailText(text, foreground, bold, strikeout);
    }

    public void setTailText(final String text, final Color foreground, final boolean bold, final boolean strikeout) {
      final Color fg = mySelected
                       ? SELECTED_FOREGROUND_COLOR
                       : foreground == null
                         ? myForeground
                         : foreground;
      setTailTextLabel(myBackground, fg, text, bold, strikeout);
    }

    public void setTypeText(final String text, final Icon icon) {
      setTypeTextLabel(myItem, myBackground, myForeground, myList, text, icon);
    }

    public boolean isReal() {
      return true;
    }

    public void setContext(final LookupElement item, final Color background, final Color foreground, final JList list, final boolean selected) {
      myItem = item;
      myBackground = background;
      myForeground = foreground;
      myList = list;
      mySelected = selected;
    }

  }

  private class WidthCalculatingPresentation extends LookupElementPresentation {
    private int myTotalWidth = 2;
    private int myIconWidth = 0;

    @Override
    public boolean isReal() {
      return false;
    }

    @Override
    public void setIcon(final Icon icon) {
      if (icon != null) {
        myIconWidth = icon.getIconWidth();
      }
    }

    @Override
    public void setItemText(@Nullable final String text, final boolean strikeout, final boolean bold) {
      addWidth(text);
    }

    @Override
    public void setTailText(@Nullable final String text, final boolean grayed, final boolean bold, final boolean strikeout) {
      addWidth(text);
    }

    @Override
    public void setTailText(final String text, final Color foreground, final boolean bold, final boolean strikeout) {
      addWidth(text);
    }

    public void setTypeText(@Nullable final String text, @Nullable final Icon icon) {
      addWidth(text + "XXX");
      if (icon != null) {
        myTotalWidth += icon.getIconWidth()+2;
      }
    }

    private void addWidth(@Nullable final String text) {
      if (text != null) {
        myTotalWidth += text.length() * myFontWidth;
      }
    }

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
