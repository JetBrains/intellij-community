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

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ToolbarDecoratorActionPromoter implements ActionPromoter {
  private static final Comparator<AnAction> ACTION_BUTTONS_SORTER = new Comparator<AnAction>() {
    @Override
    public int compare(AnAction a1, AnAction a2) {
      if (a1 instanceof AnActionButton && a2 instanceof AnActionButton) {
        final JComponent c1 = ((AnActionButton)a1).getContextComponent();
        final JComponent c2 = ((AnActionButton)a2).getContextComponent();
        return c1.hasFocus() ? -1 : c2.hasFocus() ? 1 : 0;
      }
      return 0;
    }
  };

  @Override
  public List<AnAction> promote(List<AnAction> actions, DataContext context) {
    final SortedList<AnAction> result = new SortedList<AnAction>(ACTION_BUTTONS_SORTER);
    result.addAll(actions);
    return result;
  }
}
