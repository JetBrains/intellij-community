// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunAnythingItemBase extends RunAnythingItem {
  @NotNull private final String myCommand;
  @Nullable protected final Icon myIcon;

  public RunAnythingItemBase(@NotNull String command, @Nullable Icon icon) {
    myCommand = command;
    myIcon = icon;
  }

  @NotNull
  @Override
  public String getCommand() {
    return myCommand;
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.append(myCommand);
    component.appendTextPadding(20);
    setupIcon(component, myIcon);
    return component;
  }

  public void setupIcon(@NotNull SimpleColoredComponent component, @Nullable Icon icon) {
    component.setIcon(ObjectUtils.notNull(icon, EmptyIcon.ICON_16));
    component.setIconTextGap(5);
    component.setIpad(JBUI.insets(0, 10, 0, 0));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RunAnythingItemBase base = (RunAnythingItemBase)o;

    if (!myCommand.equals(base.myCommand)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myCommand.hashCode();
  }

  protected static void appendDescription(@NotNull SimpleColoredComponent component, @Nullable String description) {
    if (description != null) {
      component.append(description, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      component.appendTextPadding(480, SwingConstants.RIGHT);
    }
  }
}
