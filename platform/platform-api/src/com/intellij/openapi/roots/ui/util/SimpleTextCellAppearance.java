// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.roots.ui.ModifiableCellAppearanceEx;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

// todo: move to intellij.platform.lang.impl ?
public class SimpleTextCellAppearance implements ModifiableCellAppearanceEx {
  private Icon myIcon;
  private final SimpleTextAttributes myTextAttributes;
  private final @NlsContexts.Label String myText;

  public static SimpleTextCellAppearance regular(@NotNull final @NlsContexts.Label String text, @Nullable final Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static SimpleTextCellAppearance invalid(@NotNull final @NlsContexts.Label String text, @Nullable final Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  public static SimpleTextCellAppearance synthetic(@NotNull final @NlsContexts.Label String text, @Nullable final Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public SimpleTextCellAppearance(@NotNull final @NlsContexts.Label String text,
                                  @Nullable final Icon icon,
                                  @NotNull final SimpleTextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
    myText = text;
  }

  @Override
  public void customize(@NotNull final SimpleColoredComponent component) {
    component.setIcon(myIcon);
    component.append(myText, myTextAttributes);
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  @Override
  public void setIcon(@Nullable final Icon icon) {
    myIcon = icon;
  }
}