package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.ide.IconUtilEx;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.StrikeoutLabel;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;

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
  private boolean myHasNonTemplates;
  private int myMaxTemplateDescriptionLength = 0;

  public LookupCellRenderer(LookupImpl lookup) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NORMAL_FONT = scheme.getFont(EditorFontType.PLAIN);
    BOLD_FONT = scheme.getFont(EditorFontType.BOLD);
    SMALL_FONT = NORMAL_FONT;

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    SHOW_SIGNATURES = settings.SHOW_SIGNATURES_IN_LOOKUPS;
    ICON_FLAGS = SHOW_SIGNATURES ? Iconable.ICON_FLAG_VISIBILITY : 0;
    EMPTY_ICON = IconUtilEx.getEmptyIcon(SHOW_SIGNATURES);
    ICON_WIDTH = EMPTY_ICON.getIconWidth();

    myLookup = lookup;
    myLabel0 = new StrikeoutLabel("", SwingConstants.LEFT);
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
    label.setText("A");
    label.setIcon(null);
    label.setFont(NORMAL_FONT);
    FONT_WIDTH = label.getPreferredSize().width;

    final LookupItem[] items = lookup.getItems();
    if (items.length > 0) lookup.getList().setPrototypeCellValue(items[0]);
    
    for (LookupItem item : items) {
      if (!(item.getObject() instanceof Template)) {
        myHasNonTemplates = true;
        break;
      }
      else {
        Template template = (Template)item.getObject();
        myMaxTemplateDescriptionLength = Math.max(myMaxTemplateDescriptionLength, template.getDescription().length());
      }
    }
  }

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean hasFocus) {

    LookupItem item = (LookupItem)value;
    JLabel label0 = getLabel0(item);
    JLabel label1 = getLabel1(item);
    JLabel label2 = getLabel2(item);
    JLabel label3 = getLabel3(item);
    Color background = isSelected ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR;
    Color foreground = isSelected ? SELECTED_FOREGROUND_COLOR : FOREGROUND_COLOR;

    Color sampleBackground = background;

    if (item.getObject() instanceof LookupValueWithUIHint &&
        label3.getText().length() == 0
       ) {
      Color proposedBackground = ((LookupValueWithUIHint)item.getObject()).getColorHint();

      if (proposedBackground == null) {
        proposedBackground = BACKGROUND_COLOR;
      }

      sampleBackground = proposedBackground;
      label3.setText("  ");
    }

    if (item.getAttribute(LookupImpl.EMPTY_ITEM_ATTRIBUTE) != null){
      foreground = EMPTY_ITEM_FOREGROUND_COLOR;
    }
    label0.setBackground(background);
    label1.setBackground(background);
    label2.setBackground(background);
    label3.setBackground(sampleBackground);
    label0.setForeground(isSelected ? SELECTED_PREFIX_FOREGROUND_COLOR : PREFIX_FOREGROUND_COLOR);
    label1.setForeground(foreground);
    label2.setForeground(foreground);
    label3.setForeground(foreground);

    boolean isSmall = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;
    if (isSmall){
      label2.setForeground(isSelected ? SELECTED_GRAYED_FOREGROUND_COLOR : GRAYED_FOREGROUND_COLOR);
    }

    return myPanel;
  }

  private boolean isBold(Object o) {
    return o instanceof PsiKeyword ||
           o instanceof PsiExpression ||
           (o instanceof LookupValueWithUIHint &&
            ((LookupValueWithUIHint)o).isBold()
           );
  }

  private JLabel getLabel0(LookupItem item){
    Object o = item.getObject();
    String prefix = myLookup.getPrefix().toLowerCase();
    String name = getName(item);
    String text;
    Icon icon;
    if (prefix.length() > 0 && name.toLowerCase().startsWith(prefix)){
      text = name.substring(0, prefix.length());
      icon = getIcon(item);
    }
    else{
      text = "";
      icon = null;
    }
    boolean highlighted = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;
    boolean bold = highlighted || isBold(o);
    boolean strikeout = isToStrikeout(item);

    StrikeoutLabel label = myLabel0;
    label.setText(text);
    label.setIcon(icon);
    label.setFont(bold ? BOLD_FONT : NORMAL_FONT);
    label.setStrikeout(strikeout);
    return label;
  }

  private JLabel getLabel1(LookupItem item){
    Object o = item.getObject();
    String prefix = myLookup.getPrefix().toLowerCase();
    String name = getName(item);
    String text;
    Icon icon;
    if (prefix.length() > 0 && name.toLowerCase().startsWith(prefix)){
      text = name.substring(prefix.length());
      icon = null;
    }
    else{
      text = name;
      icon = getIcon(item);
    }
    boolean highlighted = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;
    boolean bold = highlighted || isBold(o);
    boolean overstrike = isToStrikeout(item);

    StrikeoutLabel label = myLabel1;
    label.setText(text);
    label.setIcon(icon);
    label.setFont(bold ? BOLD_FONT : NORMAL_FONT);
    label.setStrikeout(overstrike);
    return label;
  }

  private JLabel getLabel2(final LookupItem item){
    String text = null;

    if (showSignature(item)){
      Object o = item.getObject();
      if (o instanceof PsiElement) {
        final PsiElement element = (PsiElement)o;
        if (element.isValid() && element instanceof PsiMethod){
          PsiMethod method = (PsiMethod)element;
          final PsiSubstitutor substitutor = (PsiSubstitutor) item.getAttribute(LookupItem.SUBSTITUTOR);
          text = PsiFormatUtil.formatMethod(method,
                                            substitutor != null ? substitutor : PsiSubstitutor.EMPTY,
                                            PsiFormatUtil.SHOW_PARAMETERS,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
        }
      }
    }

    String tailText = (String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    if (tailText != null){
      if (text == null){
        text = tailText;
      }
      else{
        text += tailText;
      }
    }
    if(item.getAttribute(LookupItem.INDICATE_ANONYMOUS) != null){
      if(item.getObject() instanceof PsiClass){
        final PsiClass psiClass = (PsiClass) item.getObject();
        if(psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)){
          text += "{...}";
        }
      }
    }

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
    label.setFont(isSmall ? SMALL_FONT : NORMAL_FONT);
    boolean overstrike = isToStrikeout(item);
    label.setStrikeout(overstrike);
    return label;
  }

  private boolean showSignature(LookupItem item) {
    return SHOW_SIGNATURES || item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null;
  }

  private JLabel getLabel3(LookupItem item){
    myLabel3.setHorizontalTextPosition(JLabel.RIGHT);

    Object o = item.getObject();
    String text = null;
    if (o instanceof PsiElement){
      if (showSignature(item)) {
        PsiType typeAttr = (PsiType)item.getAttribute(LookupItem.TYPE_ATTR);
        if (typeAttr != null){
          text = typeAttr.getPresentableText();
        }
        else{
          final PsiElement element = (PsiElement)o;
          if (element.isValid()) {
            if (element instanceof PsiMethod){
              PsiMethod method = (PsiMethod)element;
              if (!method.isConstructor()){
                final PsiSubstitutor substitutor = (PsiSubstitutor) item.getAttribute(LookupItem.SUBSTITUTOR);
                if (substitutor != null) {
                  text = substitutor.substitute(method.getReturnType()).getPresentableText();
                }
                else {
                  text = method.getReturnType().getPresentableText();
                }
              }
            }
            else if (element instanceof PsiVariable){
              PsiVariable variable = (PsiVariable)element;
              text = variable.getType().getPresentableText();
            }
            else if (element instanceof PsiExpression){
              PsiExpression expression = (PsiExpression)element;
              PsiType type = expression.getType();
              if (type != null){
                text = type.getPresentableText();
              }
            }
          }
        }
      }
    }
    else if (o instanceof Template){
      text = getTemplateDescriptionString((Template)o);
    } else if (o instanceof LookupValueWithUIHint) {
      text = ((LookupValueWithUIHint)o).getTypeHint();
    }

    JLabel label = myLabel3;
    label.setText(text != null ? text + " " : "");
    label.setFont(NORMAL_FONT);
    return label;
  }

  private static String getName(final LookupItem item){
    final Object o = item.getObject();
    String name = null;
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        name = PsiUtil.getName(element);

        if (element instanceof PsiAnonymousClass) {
          name = null;
        }
        else if(element instanceof PsiClass){
          PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
          if (substitutor != null && !substitutor.isValid()) {
            PsiType type = (PsiType)item.getAttribute(LookupItem.TYPE);
            if (type != null) {
              name = type.getPresentableText();
            }
          }
          else {
            name = LookupItemUtil.formatTypeName((PsiClass)element, substitutor);
          }
        }
        else if (element instanceof PsiKeyword || element instanceof PsiExpression || element instanceof PsiTypeElement){
          name = element.getText();
        }
      }
    }
    else if (o instanceof PsiType){
      name = ((PsiType)o).getPresentableText();
    }
    else if (o instanceof Template){
      name = getKeyString((Template)o);
    }
    else if (o instanceof XmlElementDescriptor) {
      name = ((XmlElementDescriptor)o).getDefaultName();
    }
    else if (o instanceof PsiMetaData) {
      name = ((PsiMetaData)o).getName();
    }
    else if (o instanceof PresentableLookupValue ) {
      name = ((PresentableLookupValue)o).getPresentation();
    } else {
      name = String.valueOf(o);
    }
    if (name == null){
      name = "";
    }

    if(item.getAttribute(LookupItem.FORCE_QUALIFY) != null){
      if (o instanceof PsiMember && ((PsiMember)o).getContainingClass() != null) {
        name = ((PsiMember)o).getContainingClass().getName() + "." + name;
      }
    }

    return name;
  }

  private Icon getIcon(LookupItem item){
    Icon iconAttr = (Icon)item.getAttribute(LookupItem.ICON_ATTR);
    if (iconAttr != null) return iconAttr;
    Icon icon = null;
    Object o = item.getObject();
    if (o instanceof LookupValueWithPsiElement) {
      o = ((LookupValueWithPsiElement)o).getElement();
    }
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        icon = element.getIcon(ICON_FLAGS);
      }
    }
    if (icon == null){
      icon = EMPTY_ICON;
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
    PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
    Object o = item.getObject();
    String text = getName(item);

    substitutor = substitutor != null ? substitutor : PsiSubstitutor.EMPTY;

    if (o instanceof PsiElement) {
      PsiElement element = (PsiElement)o;
      if (element instanceof PsiMethod){
        PsiMethod method = (PsiMethod)element;
        if (showSignature(item)){
          String parms = PsiFormatUtil.formatMethod(
            method,
            substitutor,
            PsiFormatUtil.SHOW_PARAMETERS,
            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
          text += parms;
        }
      }
    }

    final @NonNls String TYPE_GAP = "XXX";
    if (o instanceof PsiElement) {
      if (showSignature(item)){
        PsiType typeAttr = (PsiType)item.getAttribute(LookupItem.TYPE_ATTR);
        if (typeAttr != null){
          text += typeAttr.getPresentableText() + TYPE_GAP;
        }
        else{
          PsiElement element = (PsiElement)o;
          if (element instanceof PsiMethod){
            PsiMethod method = (PsiMethod)element;
            if (!method.isConstructor()){
              text += substitutor.substitute(method.getReturnType()).getPresentableText() + TYPE_GAP;
            }
          }
          else if (element instanceof PsiVariable){
            PsiVariable variable = (PsiVariable)element;
            text += substitutor.substitute(variable.getType()).getPresentableText() + TYPE_GAP;
          }
          else if (element instanceof PsiExpression){
            PsiExpression expression = (PsiExpression)element;
            PsiType type = expression.getType();
            if (type != null){
              text += type.getPresentableText() + TYPE_GAP;
            }
          }
        }
      }
    }
    else if (o instanceof Template){
      text += getTemplateDescriptionString((Template)o) + TYPE_GAP;
    } else if (o instanceof LookupValueWithUIHint &&
               ((LookupValueWithUIHint)o).getTypeHint() != null) {
      text += ((LookupValueWithUIHint)o).getTypeHint() + TYPE_GAP;
    }

    if(item.getAttribute(LookupItem.FORCE_QUALIFY) != null){
      if (o instanceof PsiMember && ((PsiMember)o).getContainingClass() != null) {
        text = ((PsiMember)o).getContainingClass().getName() + "." + text;
      }
    }


    int width = myPanel.getFontMetrics(NORMAL_FONT).stringWidth(text) + 2;

    String tailText = (String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    if(item.getAttribute(LookupItem.INDICATE_ANONYMOUS) != null){
      if(item.getObject() instanceof PsiClass){
        final PsiClass psiClass = (PsiClass) item.getObject();
        if(psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)){
          tailText += "{...}";
        }
      }
    }

    if (tailText != null){
      boolean isSmall = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;
      FontMetrics fontMetrics = myPanel.getFontMetrics(isSmall ? SMALL_FONT : NORMAL_FONT);
      width += fontMetrics.stringWidth(tailText);
    }

    return width;
  }

  private boolean isToStrikeout(LookupItem item) {
    final PsiMethod[] allMethods = (PsiMethod[])item.getAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE);
    if (allMethods != null){
      for (PsiMethod method : allMethods) {
        if (!method.isValid()) { //?
          return false;
        }
        if (!isDeprecated(method)) {
          return false;
        }
      }
      return true;
    }
    else{
      Object o = item.getObject();
      if (o instanceof PsiElement) {
        final PsiElement element = (PsiElement)o;
        if (element.isValid()) {
          return isDeprecated(element);
        }
      }
    }
    return false;
  }

  private static boolean isDeprecated(PsiElement element) {
    if (!(element instanceof PsiDocCommentOwner)) return false;
    return ((PsiDocCommentOwner)element).isDeprecated();
  }

  private static String getKeyString(Template template) {
    return template.getKey();
  }

  private String getTemplateDescriptionString(Template template) {
    int max = MAX_LENGTH - TemplateSettings.getInstance().getMaxKeyLength();
    max = Math.min(max, myMaxTemplateDescriptionLength + 1);

    StringBuffer buffer = new StringBuffer(max);
    buffer.append(' ');
    buffer.append(template.getDescription());
    if (buffer.length() > max){
      buffer.setLength(max - "...".length());
      buffer.append("...");
    }
    else if (!myHasNonTemplates){
      while(buffer.length() < max){
        buffer.append(' ');
      }
    }
    return buffer.toString();
  }
}