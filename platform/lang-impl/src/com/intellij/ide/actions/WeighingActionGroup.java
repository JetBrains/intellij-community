// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@ApiStatus.Internal
public abstract class WeighingActionGroup extends ActionGroup implements ActionWithDelegate<ActionGroup> {
  public static final Key<Double> WEIGHT_KEY = Key.create("WeighingActionGroup.WEIGHT");
  public static final Double DEFAULT_WEIGHT = 0.;
  public static final Double HIGHER_WEIGHT = 42.;

  @Override
  public abstract @NotNull ActionGroup getDelegate();

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return getDelegate().getActionUpdateThread();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    getDelegate().update(e);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return getDelegate().getChildren(e);
  }

  @Override
  public @Unmodifiable @NotNull List<@NotNull AnAction> postProcessVisibleChildren(@NotNull AnActionEvent e,
                                                                                   @NotNull List<? extends @NotNull AnAction> visibleChildren) {
    LinkedHashSet<AnAction> heaviest = null;
    double maxWeight = DEFAULT_WEIGHT.doubleValue();
    for (AnAction action : visibleChildren) {
      Presentation presentation = e.getUpdateSession().presentation(action);
      if (!presentation.isEnabled() || !presentation.isVisible()) {
        continue;
      }
      double weight = ObjectUtils.notNull(presentation.getClientProperty(WEIGHT_KEY), DEFAULT_WEIGHT).doubleValue();
      if (weight > maxWeight) {
        maxWeight = weight;
        heaviest = new LinkedHashSet<>();
      }
      if (weight == maxWeight && heaviest != null) {
        heaviest.add(action);
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
    other.getTemplatePresentation().setText(IdeBundle.messagePointer("action.presentation.WeighingActionGroup.text"));
    other.getTemplatePresentation().setPopupGroup(true);
    return JBIterable.from(chosen).append(new Separator()).append(other).toList();
  }

  protected boolean shouldBeChosenAnyway(@NotNull AnAction action) {
    return false;
  }

}
