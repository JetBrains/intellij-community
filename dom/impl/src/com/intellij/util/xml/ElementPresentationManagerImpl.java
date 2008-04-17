/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.ui.RowIcon;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationManagerImpl extends ElementPresentationManager {
  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer, int iconFlags) {
    ArrayList<Object> result = new ArrayList<Object>(elements.size());
    for (T element: elements) {
      String name = namer.fun(element);
      if (name != null) {
        Icon icon = getIcon(element);
        if (icon != null && (iconFlags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
          RowIcon baseIcon = new RowIcon(2);
          Icon emptyIcon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
          baseIcon.setIcon(icon, 0);
          baseIcon.setIcon(emptyIcon, 1);
          icon = baseIcon;
        }
        Object value = LookupValueFactory.createLookupValue(name, icon);
        result.add(value);
      }
    }
    return result.toArray();
  }

}
