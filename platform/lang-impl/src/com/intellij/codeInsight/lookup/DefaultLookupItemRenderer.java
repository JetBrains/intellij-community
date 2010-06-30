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
package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.SizedIcon;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class DefaultLookupItemRenderer extends LookupElementRenderer<LookupItem>{
  public static final DefaultLookupItemRenderer INSTANCE = new DefaultLookupItemRenderer();
  private static final Icon SAMPLE_ICON = Icons.CLASS_ICON;

  public void renderElement(final LookupItem item, final LookupElementPresentation presentation) {
    presentation.setIcon(getRawIcon(item, presentation.isReal()));

    presentation.setItemText(getName(item));
    presentation.setStrikeout(isToStrikeout(item));
    presentation.setItemTextBold(item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null);
    presentation.setTailText(getText2(item), item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null);
    presentation.setTypeText(getText3(item), null);
  }

  @Nullable
  public static Icon getRawIcon(final LookupItem item, boolean real) {
    final Icon icon = _getRawIcon(item, real);
    if (icon != null && icon.getIconHeight() > SAMPLE_ICON.getIconHeight()) {
      return new SizedIcon(icon, icon.getIconWidth(), SAMPLE_ICON.getIconHeight());
    }
    return icon;
  }

  @Nullable
  private static Icon _getRawIcon(LookupItem item, boolean real) {
    Icon icon = (Icon)item.getAttribute(LookupItem.ICON_ATTR);
    if (icon != null) return icon;

    Object o = item.getObject();

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (!real) {
      if (item.getObject() instanceof String) {
        return new EmptyIcon(0, 0);
      }

      return new EmptyIcon(SAMPLE_ICON.getIconWidth() * 2, SAMPLE_ICON.getIconHeight());
    }

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
    return item.getAttribute(LookupItem.DEPRECATED_ATTR) != null;
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
