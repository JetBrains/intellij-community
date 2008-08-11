/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.openapi.util.Iconable;
import com.intellij.codeInsight.CodeInsightSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class DefaultLookupItemRenderer extends LookupElementRenderer<LookupItem>{
  public static final DefaultLookupItemRenderer INSTANCE = new DefaultLookupItemRenderer();

  public void renderElement(final LookupItem item, final LookupElementPresentation presentation) {
    presentation.setIcon(getRawIcon(item));
    final boolean bold = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;
    final boolean grayed = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;

    presentation.setItemText(getName(item), isToStrikeout(item), bold);
    presentation.setTailText(getText2(item), grayed, false, isToStrikeout(item));
    presentation.setTypeText(getText3(item), null);
  }

  @Nullable
  public static Icon getRawIcon(final LookupItem item) {
    Icon icon = (Icon)item.getAttribute(LookupItem.ICON_ATTR);
    if (icon != null) return icon;

    Object o = item.getObject();

    int flags = CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ? Iconable.ICON_FLAG_VISIBILITY : 0;
    if (o instanceof Iconable && !(o instanceof PsiElement)) {
      return ((Iconable)o).getIcon(flags);
    }

    if (o instanceof LookupValueWithPsiElement) {
      o = ((LookupValueWithPsiElement)o).getElement();
    }
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        return element.getIcon(flags);
      }
    }
    return null;
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

  private static String getText2(final LookupItem item) {
    return (String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR);
  }

  private static boolean isToStrikeout(LookupItem item) {
    Object o = item.getObject();
    return o instanceof LookupValueWithUIHint2 && ((LookupValueWithUIHint2)o).isStrikeout();
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

}
