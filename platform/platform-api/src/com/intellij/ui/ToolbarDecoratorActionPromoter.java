/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

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
  public List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    final SortedList<AnAction> result = new SortedList<>(ACTION_BUTTONS_SORTER);
    result.addAll(actions);
    return result;
  }
}
