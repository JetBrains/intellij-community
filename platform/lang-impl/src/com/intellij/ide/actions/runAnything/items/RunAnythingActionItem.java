// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunAnythingActionItem<T extends AnAction> extends RunAnythingItemBase {
  public RunAnythingActionItem(@NotNull T action, @NotNull String fullCommand, @Nullable Icon icon) {
    super(fullCommand, icon);
  }

  @NotNull
  public static String getCommand(@NotNull AnAction action, @NotNull String command) {
    return command + " " + (action.getTemplatePresentation().getText() != null ? action.getTemplatePresentation().getText() : "undefined");
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.append(getCommand());
    setupIcon(component, myIcon);

    return component;
  }
}