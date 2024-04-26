// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.util.TextWithIcon;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public /*final*/ class PsiElementModuleRenderer extends DefaultListCellRenderer {

  private final @NotNull Function<Object, @Nullable TextWithIcon> myTextWithIconProvider;
  private @Nls String myText;

  PsiElementModuleRenderer(@NotNull Function<Object, @Nullable TextWithIcon> provider) {
    myTextWithIconProvider = provider;
  }

  @Override
  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {
    final Component listCellRendererComponent = super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
    customizeCellRenderer(value, isSelected);
    return listCellRendererComponent;
  }

  @Override
  public String getText() {
    return myText;
  }

  private void customizeCellRenderer(Object value, boolean selected) {
    myText = "";
    TextWithIcon textWithIcon = myTextWithIconProvider.apply(value);
    if (textWithIcon != null) {
      myText = textWithIcon.getText();
      setIcon(textWithIcon.getIcon());
    }
    else {
      myText = "";
    }

    setText(myText);
    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding()));
    setHorizontalTextPosition(SwingConstants.LEFT);
    setHorizontalAlignment(SwingConstants.RIGHT); // align icon to the right
    setBackground(selected ? UIUtil.getListSelectionBackground(true) : UIUtil.getListBackground());
    setForeground(selected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());
  }
}
