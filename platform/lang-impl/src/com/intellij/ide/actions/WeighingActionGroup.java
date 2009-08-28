/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public abstract class WeighingActionGroup extends ActionGroup {
  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  @Override
  public void update(AnActionEvent e) {
    getDelegate().update(e);
  }

  protected abstract ActionGroup getDelegate();

  private static void getAllChildren(@Nullable AnActionEvent e, ActionGroup group, List<AnAction> result) {
    for (final AnAction action : group.getChildren(e)) {
      if (action instanceof ActionGroup && !((ActionGroup) action).isPopup()) {
        getAllChildren(e, (ActionGroup) action, result);
      } else {
        result.add(action);
      }
    }
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    final AnAction[] children = getDelegate().getChildren(e);
    if (e == null) {
      return children;
    }

    final ArrayList<AnAction> all = new ArrayList<AnAction>();
    getAllChildren(e, getDelegate(), all);

    LinkedHashSet<AnAction> heaviest = null;
    double maxWeight = Presentation.DEFAULT_WEIGHT;
    for (final AnAction action : all) {
      final Presentation presentation = myPresentationFactory.getPresentation(action);
      presentation.setWeight(Presentation.DEFAULT_WEIGHT);
      Utils.updateGroupChild(e.getDataContext(), e.getPlace(), action, presentation);
      if (presentation.isEnabled() && presentation.isVisible()) {
        if (presentation.getWeight() > maxWeight) {
          maxWeight = presentation.getWeight();
          heaviest = new LinkedHashSet<AnAction>();
        }
        if (presentation.getWeight() == maxWeight && heaviest != null) {
          heaviest.add(action);
        }
      }
    }

    if (heaviest == null) {
      return children;
    }

    final DefaultActionGroup chosen = new DefaultActionGroup();
    boolean prevSeparator = true;
    for (final AnAction action : all) {
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

    final ActionGroup other = new ExcludingActionGroup(getDelegate(), heaviest);
    other.setPopup(true);
    other.getTemplatePresentation().setText("Other...");
    return new AnAction[]{chosen, new Separator(), other};
  }

  protected boolean shouldBeChosenAnyway(AnAction action) {
    return false;
  }

}
