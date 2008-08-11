package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.StrikeoutLabel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class LookupCellRenderer implements ListCellRenderer {
  private final int myIconFlags;
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
  private final StrikeoutLabel myLabel2; // type
  private final JLabel myLabel3; // type
  private final JPanel myPanel;

  private final LookupElementPresentationImpl myLookupElementPresentation = new LookupElementPresentationImpl();

  public LookupCellRenderer(LookupImpl lookup) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NORMAL_FONT = scheme.getFont(EditorFontType.PLAIN);
    BOLD_FONT = scheme.getFont(EditorFontType.BOLD);

    myIconFlags = CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ? Iconable.ICON_FLAG_VISIBILITY : 0;

    myLookup = lookup;
    myNameComponent = new MySimpleColoredComponent();
    myNameComponent.setIpad(new Insets(0, 0, 0, 0));
    myLabel2 = new StrikeoutLabel("", SwingConstants.LEFT);
    myLabel2.setOpaque(true);
    myLabel3 = new JLabel("", SwingConstants.LEFT);
    myLabel3.setOpaque(true);
    myPanel = new JPanel(new BorderLayout()){
      public void paint(Graphics g){
        UISettings.setupAntialiasing(g);
        super.paint(g);
      }
    };
    myPanel.add(myNameComponent, BorderLayout.WEST);
    myPanel.add(myLabel2, BorderLayout.CENTER);
    myLabel2.setBorder(new EmptyBorder(0, 0, 0, 10));
    myPanel.add(myLabel3, BorderLayout.EAST);

    JLabel label = myLabel3;
    //noinspection HardCodedStringLiteral
    label.setText("W"); //the widest letter known to me
    label.setIcon(null);
    label.setFont(BOLD_FONT);
    myFontWidth = label.getPreferredSize().width;

    final LookupElement[] items = lookup.getItems();
    if (items.length > 0) lookup.getList().setPrototypeCellValue(items[0]);

    myLookupElementPresentation.setItems(items);

    UIUtil.removeQuaquaVisualMarginsIn(myPanel);
  }

  public int getFontWidth() {
    return myFontWidth;
  }

  public void updateIconWidth(final int iconWidth) {
    if (iconWidth > myEmptyIcon.getIconWidth()) {
      myEmptyIcon = new EmptyIcon(iconWidth, 2);
    }
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
      background = isSelected ? SELECTED_BACKGROUND_COLOR : new Color(220, 245, 220);
    }

    myLookupElementPresentation.setContext(item, background, foreground, list, isSelected);

    myNameComponent.clear();
    myLabel2.setBackground(background);
    item.renderElement(myLookupElementPresentation);

    return myPanel;
  }

  private void setItemTextLabels(LookupElement item, final Color background, final Color foreground, final boolean selected, final String name,
                                 final boolean toStrikeout, boolean bold) {
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

  private void setTailTextLabel(final Color background, Color foreground, final boolean selected, final String text,
                                final Font font, final boolean strikeout) {
    StrikeoutLabel label = myLabel2;
    if (text != null){
      label.setText(text);
    }
    else{
      label.setText("");
    }
    label.setStrikeout(strikeout);

    label.setBackground(background);
    label.setForeground(foreground);
    label.setFont(font);
  }

  private void setTypeTextLabel(LookupElement item, final Color background, Color foreground, JList list, final String text3, final Icon icon){
    myLabel3.setHorizontalTextPosition(SwingConstants.RIGHT);
    myLabel3.setIcon(icon);

    String text = text3;

    final int listWidth = Math.max(list.getFixedCellWidth(), list.getWidth());
    final int maxWidth = listWidth - myNameComponent.getPreferredSize().width - 3 * myFontWidth;

    JLabel label = myLabel3;
    if (text == null) text = "";

    else text += " ";

    label.setText(text);
    label.setFont(NORMAL_FONT);

    if (maxWidth > 0) {
      while (label.getPreferredSize().width > maxWidth) {
        String repl = text.replaceFirst("<((<\\.\\.\\.>)|[^<>])*>", "<...>");
        if (repl.equals(text)) {
          //text = "...";
          break;
        }
        text = repl;
        label.setText(text);
      }
    }


    Color sampleBackground = background;

    Object o = item.getObject();
    if (o instanceof LookupValueWithUIHint && label.getText().length() == 0) {
      Color proposedBackground = ((LookupValueWithUIHint)o).getColorHint();

      if (proposedBackground == null) {
        proposedBackground = BACKGROUND_COLOR;
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
    WidthCalculatingPresentation p = new WidthCalculatingPresentation(myLookupElementPresentation);
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

  private class LookupElementPresentationImpl extends UserDataHolderBase implements LookupElementPresentationEx {
    private LookupElement myItem;
    private Color myBackground;
    private Color myForeground;
    private JList myList;
    private boolean mySelected;
    private LookupElement[] myItems;

    public boolean isSelected() {
      return mySelected;
    }

    public void setIcon(final Icon icon) {
      myNameComponent.setIcon(getIcon(icon));
    }

    public void setItemText(final String text) {
      setItemText(text, false, false);
    }

    public void setItemText(@Nullable final String text, final boolean strikeout, final boolean bold) {
      setItemTextLabels(myItem, myBackground, myForeground, mySelected, text, strikeout, bold);
    }

    public void setTailText(final String text) {
      setTailText(text, null, false, false);
    }

    public void setTailText(@Nullable final String text, final boolean grayed, final boolean bold, final boolean strikeout) {
      final Color foreground = grayed
                               ? mySelected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR
                               : null;
      setTailText(text, foreground, bold, strikeout);
    }

    public void setTailText(final String text, final Color foreground, final boolean bold, final boolean strikeout) {
      final Color fg = foreground == null
                       ? mySelected ? SELECTED_FOREGROUND_COLOR : myForeground
                       : foreground;
      setTailTextLabel(myBackground, fg, mySelected, text, bold ? BOLD_FONT : NORMAL_FONT, strikeout);
    }

    public void setTypeText(final String text) {
      setTypeTextLabel(myItem, myBackground, myForeground, myList, text, null);
    }

    public void setTypeText(final String text, final Icon icon) {
      setTypeTextLabel(myItem, myBackground, myForeground, myList, text, icon);
    }

    public void setContext(final LookupElement item, final Color background, final Color foreground, final JList list, final boolean selected) {
      myItem = item;
      myBackground = background;
      myForeground = foreground;
      myList = list;
      mySelected = selected;
    }

    public void setItems(final LookupElement[] items) {
      myItems = items;
    }

    public LookupElement[] getItems() {
      return myItems;
    }

    public int getMaxLength() {
      return MAX_LENGTH;
    }

    public boolean trimText() {
      return false;
    }
  }

  private class WidthCalculatingPresentation extends LookupElementPresentationImpl {
    private LookupElementPresentationImpl myBasePresentation;
    private int myTotalWidth = 2;
    private int myIconWidth = 0;

    private WidthCalculatingPresentation(final LookupElementPresentationImpl basePresentation) {
      myBasePresentation = basePresentation;
    }

    @Override
    public void setIcon(final Icon icon) {
      if (icon != null) {
        myIconWidth = icon.getIconWidth();
      }
    }

    public void setItemText(final String text) {
      addWidth(text);
    }

    @Override
    public void setItemText(@Nullable final String text, final boolean strikeout, final boolean bold) {
      addWidth(text);
    }

    @Override
    public void setTailText(@Nullable final String text, final boolean grayed, final boolean bold, final boolean strikeout) {
      addWidth(text);
    }

    public void setTailText(final String text) {
      addWidth(text);
    }

    @Override
    public void setTailText(final String text, final Color foreground, final boolean bold, final boolean strikeout) {
      addWidth(text);
    }

    public void setTypeText(final String text) {
      addWidth(text + "XXX");
    }

    public void setTypeText(@Nullable final String text, @Nullable final Icon icon) {
      setTypeText(text);
      if (icon != null) {
        myTotalWidth += icon.getIconWidth()+2;
      }
    }

    public boolean trimText() {
      return true;
    }

    private void addWidth(@Nullable final String text) {
      if (text != null) {
        myTotalWidth += text.length() * myFontWidth;
      }
    }

    public LookupElement[] getItems() {
      return myBasePresentation.getItems();
    }

    public <T> T getUserData(final Key<T> key) {
      return myBasePresentation.getUserData(key);
    }

    public <T> void putUserData(final Key<T> key, final T value) {
      myBasePresentation.putUserData(key, value);
    }
  }

  private static class MySimpleColoredComponent extends SimpleColoredComponent {
    private MySimpleColoredComponent() {
      setFocusBorderAroundIcon(true);
      setBorderInsets(new Insets(0, 0, 0, 0));
    }
  }
}
