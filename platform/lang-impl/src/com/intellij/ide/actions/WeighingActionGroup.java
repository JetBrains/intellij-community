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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author peter
 */
public abstract class WeighingActionGroup extends ActionGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.WeighingActionGroup");
  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  @Override
  public void update(AnActionEvent e) {
    getDelegate().update(e);
  }

  protected abstract ActionGroup getDelegate();

  private static void getAllChildren(@Nullable AnActionEvent e, ActionGroup group, List<AnAction> result) {
    for (final AnAction action : group.getChildren(e)) {
      if (action == null) {
        LOG.error("Null child for " + group + " of class " + group.getClass());
        continue;
      }
      if (action instanceof ActionGroup && !((ActionGroup)action).isPopup()) {
        getAllChildren(e, (ActionGroup)action, result);
      }
      else {
        result.add(action);
      }
    }
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    final AnAction[] children = getDelegate().getChildren(e);
    if (e == null) {
      return children;
    }

    final ArrayList<AnAction> all = new ArrayList<>();
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
          heaviest = new LinkedHashSet<>();
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
