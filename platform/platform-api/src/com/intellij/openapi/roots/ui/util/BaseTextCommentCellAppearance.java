// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class BaseTextCommentCellAppearance implements CellAppearanceEx {
  private SimpleTextAttributes myCommentAttributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
  private SimpleTextAttributes myTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

  protected abstract Icon getIcon();

  protected abstract @Nls String getSecondaryText();

  protected abstract @Nls String getPrimaryText();

  @Override
  public void customize(final @NotNull SimpleColoredComponent component) {
    component.setIcon(getIcon());
    component.append(getPrimaryText(), myTextAttributes);
    final String secondaryText = getSecondaryText();
    if (!StringUtil.isEmptyOrSpaces(secondaryText)) {
      component.append(" (" + secondaryText + ")", myCommentAttributes);
    }
  }

  @Override
  public @NotNull String getText() {
    String secondaryText = getSecondaryText();
    if (secondaryText != null && !secondaryText.isEmpty()) {
      return getPrimaryText() + " (" + secondaryText + ")";
    }
    return getPrimaryText();
  }

  public void setCommentAttributes(SimpleTextAttributes commentAttributes) {
    myCommentAttributes = commentAttributes;
  }

  public void setTextAttributes(SimpleTextAttributes textAttributes) {
    myTextAttributes = textAttributes;
  }
}