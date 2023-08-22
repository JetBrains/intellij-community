/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

abstract class WeighingActionGroup extends ActionGroup {

  @Override
  public void update(@NotNull AnActionEvent e) {
    getDelegate().update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return getDelegate().getActionUpdateThread();
  }

  protected abstract ActionGroup getDelegate();

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return getDelegate().getChildren(e);
  }

  @NotNull
  @Override
  public List<AnAction> postProcessVisibleChildren(@NotNull List<? extends AnAction> visibleChildren, @NotNull UpdateSession updateSession) {
    LinkedHashSet<AnAction> heaviest = null;
    double maxWeight = Presentation.DEFAULT_WEIGHT;
    for (AnAction action : visibleChildren) {
      Presentation presentation = updateSession.presentation(action);
      if (presentation.isEnabled() && presentation.isVisible()) {
        if (presentation.getWeight() > maxWeight) {
          maxWeight = presentation.getWeight();
          heaviest = new LinkedHashSet<>();
        }
        if (presentation.getWeight() == maxWeight && heaviest != null) {
          heaviest.add(action);
        }
      }
    }

    if (heaviest == null) {
      return new ArrayList<>(visibleChildren);
    }

    ArrayList<AnAction> chosen = new ArrayList<>();
    boolean prevSeparator = true;
    for (AnAction action : visibleChildren) {
      final boolean separator = action instanceof Separator;
      if (separator && !prevSeparator) {
        chosen.add(action);
      }
      prevSeparator = separator;

      if (shouldBeChosenAnyway(action)) {
        heaviest.add(action);
      }

      if (heaviest.contains(action)) {
        chosen.add(action);
      }
    }
    ActionGroup other = new ExcludingActionGroup(getDelegate(), heaviest);
    other.setPopup(true);
    updateSession.presentation(other).setText(IdeBundle.messagePointer("action.presentation.WeighingActionGroup.text"));
    return JBIterable.from(chosen).append(new Separator()).append(other).toList();
  }

  protected boolean shouldBeChosenAnyway(AnAction action) {
    return false;
  }

}
