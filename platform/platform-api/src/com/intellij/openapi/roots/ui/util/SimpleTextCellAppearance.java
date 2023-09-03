// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public static SimpleTextCellAppearance regular(final @NotNull @NlsContexts.Label String text, final @Nullable Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static SimpleTextCellAppearance invalid(final @NotNull @NlsContexts.Label String text, final @Nullable Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  public static SimpleTextCellAppearance synthetic(final @NotNull @NlsContexts.Label String text, final @Nullable Icon icon) {
    return new SimpleTextCellAppearance(text, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public SimpleTextCellAppearance(final @NotNull @NlsContexts.Label String text,
                                  final @Nullable Icon icon,
                                  final @NotNull SimpleTextAttributes textAttributes) {
    myIcon = icon;
    myTextAttributes = textAttributes;
    myText = text;
  }

  @Override
  public void customize(final @NotNull SimpleColoredComponent component) {
    component.setIcon(myIcon);
    component.append(myText, myTextAttributes);
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  public @NotNull SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  @Override
  public void setIcon(final @Nullable Icon icon) {
    myIcon = icon;
  }
}