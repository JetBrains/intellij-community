// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ToolbarDecoratorActionPromoter implements ActionPromoter {
  private static final Comparator<AnAction> ACTION_BUTTONS_SORTER = Comparator.comparingInt(action -> {
    if (action instanceof AnActionButton) {
      final JComponent context = ((AnActionButton)action).getContextComponent();
      return context != null && context.hasFocus() ? -1 : 0;
    }
    return 0;
  });

  @Override
  public @Unmodifiable List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
    final SortedList<AnAction> result = new SortedList<>(ACTION_BUTTONS_SORTER);
    result.addAll(actions);
    return result;
  }
}
