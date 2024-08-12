// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class RunAnythingHelpItem extends RunAnythingItemBase {
  private final @NotNull @Nls String myPlaceholder;
  private final @Nullable @NlsContexts.DetailedDescription String myDescription;

  public RunAnythingHelpItem(@NotNull @Nls String placeholder, @NotNull String command, @Nullable @NlsContexts.DetailedDescription String description, @Nullable Icon icon) {
    super(command, icon);
    myPlaceholder = placeholder;
    myDescription = description;
  }

  @Override
  public @NotNull Component createComponent(@Nullable String pattern, boolean isSelected, boolean hasFocus) {
    JPanel component = (JPanel)super.createComponent(pattern, isSelected, hasFocus);

    SimpleColoredComponent simpleColoredComponent = new SimpleColoredComponent();
    parseAndApplyStyleToParameters(simpleColoredComponent, myPlaceholder);
    appendDescription(simpleColoredComponent, myDescription, UIUtil.getListForeground(isSelected, true));
    setupIcon(simpleColoredComponent, myIcon);

    component.add(simpleColoredComponent, BorderLayout.WEST);

    return component;
  }

  private static void parseAndApplyStyleToParameters(@NotNull SimpleColoredComponent component, @NotNull @Nls String placeholder) {
    int lt = StringUtil.indexOf(placeholder, "<");
    if (lt == -1) {
      component.append(placeholder);
      return;
    }

    int gt = StringUtil.indexOf(placeholder, ">", lt);
    //appends leading
    component.append(gt > -1 ? placeholder.substring(0, lt) : placeholder);
    while (lt > -1 && gt > -1) {
      component.append(placeholder.substring(lt, gt + 1), SimpleTextAttributes.GRAY_ATTRIBUTES);

      lt = StringUtil.indexOf(placeholder, "<", gt);
      if (lt == -1) {
        component.append(placeholder.substring(gt + 1));
        break;
      }

      component.append(placeholder.substring(gt + 1, lt));
      gt = StringUtil.indexOf(placeholder, ">", lt);
    }
  }
}