// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TextWithIcon;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class PlatformModuleRendererFactory extends ModuleRendererFactory {

  @Override
  public @NotNull DefaultListCellRenderer getModuleRenderer() {
    return new PlatformModuleRenderer();
  }

  @Override
  public boolean rendersLocationString() {
    return true;
  }

  @Override
  public @Nullable TextWithIcon getModuleTextWithIcon(Object element) {
    String text = getItemText(element);
    return text == null ? null : new TextWithIcon(text, null);
  }

  public static class PlatformModuleRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(StringUtil.notNullize(getItemText(value)));
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      setHorizontalTextPosition(SwingConstants.LEFT);
      setBackground(isSelected ? UIUtil.getListSelectionBackground(true) : UIUtil.getListBackground());
      setForeground(isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());
      return component;
    }
  }

  private static @NlsSafe @Nullable String getItemText(Object value) {
    if (!(value instanceof NavigationItem)) {
      return null;
    }
    final ItemPresentation presentation = ((NavigationItem)value).getPresentation();
    if (presentation == null) {
      return null;
    }
    String containerText = presentation.getLocationString();
    if (StringUtil.isEmpty(containerText)) {
      return null;
    }
    return " " + containerText;
  }
}
