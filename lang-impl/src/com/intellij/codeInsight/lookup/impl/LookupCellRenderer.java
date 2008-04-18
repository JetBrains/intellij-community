package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.RowIcon;
import com.intellij.ui.StrikeoutLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class LookupCellRenderer implements ListCellRenderer {
  private final int ICON_FLAGS;
  private final Icon EMPTY_ICON;
  public final int ICON_WIDTH;
  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;
  private final Font SMALL_FONT;
  private final int FONT_WIDTH;

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

  private final boolean SHOW_SIGNATURES;

  private LookupImpl myLookup;

  private StrikeoutLabel myLabel0; // highlighted part of name
  private StrikeoutLabel myLabel1; // rest of name
  private StrikeoutLabel myLabel2; // parameters and tail text
  private JLabel myLabel3; // type
  private JPanel myPanel;

  private LookupElementPresentationImpl myLookupElementPresentation = new LookupElementPresentationImpl();

  public LookupCellRenderer(LookupImpl lookup) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NORMAL_FONT = scheme.getFont(EditorFontType.PLAIN);
    BOLD_FONT = scheme.getFont(EditorFontType.BOLD);
    SMALL_FONT = NORMAL_FONT;

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    SHOW_SIGNATURES = settings.SHOW_SIGNATURES_IN_LOOKUPS;
    ICON_FLAGS = SHOW_SIGNATURES ? Iconable.ICON_FLAG_VISIBILITY : 0;
    EMPTY_ICON = IconUtil.getEmptyIcon(SHOW_SIGNATURES);
    ICON_WIDTH = EMPTY_ICON.getIconWidth();

    myLookup = lookup;
    myLabel0 = new StrikeoutLabel("", SwingConstants.LEFT) {
      public Dimension getPreferredSize() {
        final Dimension preferredSize = super.getPreferredSize();
        return new Dimension(preferredSize.width + 2, preferredSize.height);
      }
    };
    myLabel0.setOpaque(true);
    myLabel1 = new StrikeoutLabel("", SwingConstants.LEFT);
    myLabel1.setOpaque(true);
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
    myPanel.add(myLabel0, BorderLayout.WEST);
    JPanel panel = new JPanel(new BorderLayout());
    myPanel.add(panel, BorderLayout.CENTER);
    panel.add(myLabel1, BorderLayout.WEST);
    panel.add(myLabel2, BorderLayout.CENTER);
    panel.add(myLabel3, BorderLayout.EAST);

    JLabel label = myLabel0;
    //noinspection HardCodedStringLiteral
    label.setText("W"); //the widest letter known to me
    label.setIcon(null);
    label.setFont(BOLD_FONT);
    FONT_WIDTH = label.getPreferredSize().width;

    final LookupItem[] items = lookup.getItems();
    if (items.length > 0) lookup.getList().setPrototypeCellValue(items[0]);

    myLookupElementPresentation.setItems(items);

    UIUtil.removeQuaquaVisualMarginsIn(myPanel);
  }

  public Component getListCellRendererComponent(
    final JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean hasFocus) {

    final LookupItem item = (LookupItem)value;
    Color background = isSelected ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR;
    final Color foreground = isSelected ? SELECTED_FOREGROUND_COLOR : FOREGROUND_COLOR;
    final int preferredCount = myLookup.getPreferredItemsCount();
    if (index <= preferredCount - 1 && preferredCount < list.getModel().getSize() - 1) {
      background = isSelected ? SELECTED_BACKGROUND_COLOR : new Color(220, 245, 220);
    }

    myLookupElementPresentation.setContext(item, background, foreground, list, isSelected);

    ElementLookupRenderer renderer = getRendererForItem(item);
    if (renderer != null) {
      myLabel2.setBackground(background);
      renderer.renderElement(item, item.getObject(), myLookupElementPresentation);
    }
    else {
      setItemTextLabels(item, background, foreground, isSelected, getName(item), isToStrikeout(item));
      setTailTextLabel(item, background, foreground, isSelected, getText2(item), null, isToStrikeout(item));
      setTypeTextLabel(item, background, foreground, list, getText3(item), null);
    }

    return myPanel;
  }

  @Nullable
  private static ElementLookupRenderer getRendererForItem(final LookupItem item) {
    for(ElementLookupRenderer renderer: Extensions.getExtensions(ElementLookupRenderer.EP_NAME)) {
      if (renderer.handlesItem(item.getObject())) return renderer;
    }
    return null;
  }

  private void setItemTextLabels(LookupItem item, final Color background, final Color foreground, final boolean selected, final String name,
                                 final boolean toStrikeout){
    final String prefix = myLookup.getPrefix();
    String text;
    Icon icon;
    if (prefix.length() > 0 && StringUtil.startsWithIgnoreCase(name, prefix)){
      text = name.substring(0, prefix.length());
      icon = getIcon(item);
      setItemTextLabel(item, background, text, icon, myLabel0, selected ? SELECTED_PREFIX_FOREGROUND_COLOR : PREFIX_FOREGROUND_COLOR,
                       toStrikeout);

      text = name.substring(prefix.length());
      icon = null;
      setItemTextLabel(item, background, text, icon, myLabel1, foreground, toStrikeout);
    }
    else{
      text = "";
      icon = null;
      setItemTextLabel(item, background, text, icon, myLabel0, selected ? SELECTED_PREFIX_FOREGROUND_COLOR : PREFIX_FOREGROUND_COLOR,
                       toStrikeout);

      text = name;
      icon = getIcon(item);
      setItemTextLabel(item, background, text, icon, myLabel1, foreground, toStrikeout);
    }
  }

  private void setItemTextLabel(final LookupItem item, final Color background, final String text, final Icon icon,
                                final StrikeoutLabel label, final Color fg, final boolean strikeout) {
    boolean bold = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;

    label.setText(text);
    label.setIcon(icon);
    label.setFont(bold ? BOLD_FONT : NORMAL_FONT);
    label.setStrikeout(strikeout);
    label.setBackground(background);
    label.setForeground(fg);
  }

  private void setTailTextLabel(final LookupItem item, final Color background, Color foreground, final boolean selected, final String text,
                                final Font forceFont, final boolean strikeout) {
    StrikeoutLabel label = myLabel2;
    if (text != null){
      String s = text;
      int width = getTextWidth(item);
      int n = width - MAX_LENGTH * FONT_WIDTH;
      if (n > 0){
        n = Math.min(n, (s.length() - 7) * FONT_WIDTH);
        if (n >= 0){
          s = s.substring(0, s.length() - n / FONT_WIDTH - 3) + "...";
        }
      }
      label.setText(s);
    }
    else{
      label.setText("");
    }
    boolean isSmall = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;
    Font font = forceFont;
    if (font == null) font = isSmall ? SMALL_FONT : NORMAL_FONT;
    label.setStrikeout(strikeout);

    label.setBackground(background);
    label.setForeground(foreground);
    label.setFont(font);
    if (isSmall){
      label.setForeground(selected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR);
    }
  }

  private static String getText2(final LookupItem item) {
    return (String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR);
  }

  private void setTypeTextLabel(LookupItem item, final Color background, Color foreground, JList list, final String text3, final Icon icon){
    myLabel3.setHorizontalTextPosition(SwingConstants.RIGHT);
    myLabel3.setIcon(icon);

    String text = text3;

    final int maxWidth =
      (list.getFixedCellWidth() - myLabel0.getPreferredSize().width - myLabel1.getPreferredSize().width - myLabel2.getPreferredSize().width) / FONT_WIDTH - 3;

    JLabel label = myLabel3;
    if (text == null) text = "";
    else text += " ";

    while (text.length() > maxWidth) {
      String repl = text.replaceFirst("<((<\\.\\.\\.>)|[^<>])*>", "<...>");
      if (repl.equals(text)) {
        //text = "...";
        break;
      }
      text = repl;
    }

    label.setText(text);
    label.setFont(NORMAL_FONT);
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

    if (item.getAttribute(LookupImpl.EMPTY_ITEM_ATTRIBUTE) != null){
      foreground = EMPTY_ITEM_FOREGROUND_COLOR;
    }
    label.setBackground(sampleBackground);
    label.setForeground(foreground);
  }

  @Nullable
  private static String getText3(final LookupItem item) {
    Object o = item.getObject();
    String text;
    if (o instanceof LookupValueWithUIHint) {
      text = ((LookupValueWithUIHint)o).getTypeHint();
    }
    else {
      text = (String)item.getAttribute(LookupItem.TYPE_TEXT_ATTR);
    }
    return text;
  }

  private static String getName(final LookupItem item){
    final String presentableText = item.getPresentableText();
    if (presentableText != null) return presentableText;
    final Object o = item.getObject();
    String name = null;
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        name = PsiUtilBase.getName(element);
      }
    }
    else if (o instanceof PsiMetaData) {
      name = ((PsiMetaData)o).getName();
    }
    else if (o instanceof PresentableLookupValue ) {
      name = ((PresentableLookupValue)o).getPresentation();
    }
    else {
      name = String.valueOf(o);
    }
    if (name == null){
      name = "";
    }

    return name;
  }

  private Icon getIcon(LookupItem item){
    Icon icon = (Icon)item.getAttribute(LookupItem.ICON_ATTR);
    if (icon == null) {
      Object o = item.getObject();

      if (o instanceof Iconable && !(o instanceof PsiElement)) {
        icon = ((Iconable)o).getIcon(ICON_FLAGS);
      } else {
        if (o instanceof LookupValueWithPsiElement) {
          o = ((LookupValueWithPsiElement)o).getElement();
        }
        if (o instanceof PsiElement) {
          final PsiElement element = (PsiElement)o;
          if (element.isValid()) {
            icon = element.getIcon(ICON_FLAGS);
          }
        }
      }
    }
    if (icon == null){
      icon = EMPTY_ICON;
    } else if (icon.getIconWidth() < EMPTY_ICON.getIconWidth()) {
      final RowIcon rowIcon = new RowIcon(2);
      rowIcon.setIcon(icon, 0);
      rowIcon.setIcon(new EmptyIcon(EMPTY_ICON.getIconWidth() - icon.getIconWidth()), 1);
      icon = rowIcon;
    }
    return icon;
  }

  public int getMaximumWidth(final LookupItem[] items){
    int maxWidth = 0;
    for (LookupItem item : items) {
      maxWidth = Math.max(maxWidth, getTextWidth(item));
    }
    maxWidth = Math.min(maxWidth, MAX_LENGTH * FONT_WIDTH);
    return maxWidth + EMPTY_ICON.getIconWidth() + myLabel0.getIconTextGap() + FONT_WIDTH;
  }

  /**
   * Should be called in atomic action.
   * @return width in pixels
   */
  private int getTextWidth(LookupItem item){
    ElementLookupRenderer renderer = getRendererForItem(item);
    if (renderer != null) {
      WidthCalculatingPresentation p = new WidthCalculatingPresentation(myLookupElementPresentation);
      renderer.renderElement(item, item.getObject(), p);
      return p.myTotalWidth;
    }

    String text = getName(item);
    final @NonNls String TYPE_GAP = "XXX";
    text += getText3(item) + TYPE_GAP;

    int width = myPanel.getFontMetrics(NORMAL_FONT).stringWidth(text) + 2;
    String text2 = getText2(item);
    if (text2 != null){
      boolean isSmall = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;
      FontMetrics fontMetrics = myPanel.getFontMetrics(isSmall ? SMALL_FONT : NORMAL_FONT);
      width += fontMetrics.stringWidth(text2);
    }

    return width;
  }

  private static boolean isToStrikeout(LookupItem item) {
    Object o = item.getObject();
    return o instanceof LookupValueWithUIHint2 && ((LookupValueWithUIHint2)o).isStrikeout();
  }

  private class LookupElementPresentationImpl extends UserDataHolderBase implements LookupElementPresentation {
    private LookupItem myItem;
    private Color myBackground;
    private Color myForeground;
    private JList myList;
    private boolean mySelected;
    private LookupItem[] myItems;

    public void setItemText(final String text) {
      setItemTextLabels(myItem, myBackground, myForeground, mySelected, text, false);
    }

    public void setItemText(final String text, boolean strikeout) {
      setItemTextLabels(myItem, myBackground, myForeground, mySelected, text, strikeout);
    }

    public void setTailText(final String text) {
      setTailTextLabel(myItem, myBackground, myForeground, mySelected, text, null, false);
    }

    public void setTailText(final String text, final boolean strikeout) {
      setTailTextLabel(myItem, myBackground, myForeground, mySelected, text, null, strikeout);
    }

    public void setTailText(final String text, final Color foreground, final boolean bold) {
      setTailTextLabel(myItem, myBackground,
                    mySelected ? SELECTED_FOREGROUND_COLOR : foreground, mySelected, text, bold ? BOLD_FONT : null, isToStrikeout(myItem));
    }

    public void setTypeText(final String text) {
      setTypeTextLabel(myItem, myBackground, myForeground, myList, text, null);
    }

    public void setTypeText(final String text, final Icon icon) {
      setTypeTextLabel(myItem, myBackground, myForeground, myList, text, icon);
    }

    public void setContext(final LookupItem item, final Color background, final Color foreground, final JList list, final boolean selected) {
      myItem = item;
      myBackground = background;
      myForeground = foreground;
      myList = list;
      mySelected = selected;
    }

    public void setItems(final LookupItem[] items) {
      myItems = items;
    }

    public LookupItem[] getItems() {
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

    private WidthCalculatingPresentation(final LookupElementPresentationImpl basePresentation) {
      myBasePresentation = basePresentation;
    }

    public void setItemText(final String text) {
      addWidth(text);
    }

    public void setItemText(final String text, final boolean strikeout) {
      addWidth(text);
    }

    public void setTailText(final String text) {
      addWidth(text);
    }

    public void setTailText(final String text, final boolean strikeout) {
      addWidth(text);
    }

    public void setTypeText(final String text) {
      addWidth(text + "XXX");
    }

    public void setTailText(final String text, final Color foreground, final boolean bold) {
      addWidth(text);
    }

    public void setTypeText(final String text, final Icon icon) {
      setTypeText(text);
      myTotalWidth += icon.getIconWidth()+2;
    }

    public boolean trimText() {
      return true;
    }

    private void addWidth(final String text) {
      myTotalWidth += myPanel.getFontMetrics(NORMAL_FONT).stringWidth(text);
    }

    public LookupItem[] getItems() {
      return myBasePresentation.getItems();
    }

    public <T> T getUserData(final Key<T> key) {
      return myBasePresentation.getUserData(key);
    }

    public <T> void putUserData(final Key<T> key, final T value) {
      myBasePresentation.putUserData(key, value);
    }
  }
}
