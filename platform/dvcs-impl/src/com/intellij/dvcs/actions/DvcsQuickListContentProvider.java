// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.actions;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.actions.VcsQuickListContentProvider;
import com.intellij.openapi.vcs.actions.VcsQuickListPopupAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class DvcsQuickListContentProvider implements VcsQuickListContentProvider {

  @Override
  @Nullable
  public List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs,
                                      @Nullable DataContext dataContext) {
    if (activeVcs == null || !replaceVcsActionsFor(activeVcs, dataContext)) return null;

    ActionManager manager = ActionManager.getInstance();
    ActionGroup vcsGroup = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_OPERATIONS_POPUP);
    if (vcsGroup == null) return null;
    return List.of(new ActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        if (e == null) return EMPTY_ARRAY;
        final List<AnAction> actions = new ArrayList<>();
        ActionGroup vcsAwareGroup = (ActionGroup)ContainerUtil.find(e.getUpdateSession().children(vcsGroup), action -> {
          AnAction adjusted = action instanceof CustomisedActionGroup o ? o.getDelegate() : action;
          return adjusted instanceof VcsQuickListPopupAction.VcsAware;
        });
        if (vcsAwareGroup != null) ContainerUtil.addAll(actions, e.getUpdateSession().children(vcsAwareGroup));
        customizeActions(manager, actions);
        return actions.toArray(EMPTY_ARRAY);
      }
    });
  }

  protected void customizeActions(@NotNull ActionManager manager, @NotNull List<? super AnAction> actions) {
    List<AnAction> providerActions = collectVcsSpecificActions(manager);
    actions.removeAll(providerActions);
    actions.add(Separator.getInstance());
    actions.addAll(providerActions);
  }

  @NonNls
  @NotNull
  protected abstract String getVcsName();

  protected abstract List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager);

  @Override
  public boolean replaceVcsActionsFor(@NotNull AbstractVcs activeVcs, @Nullable DataContext dataContext) {
    return getVcsName().equals(activeVcs.getName());
  }

  protected static void add(String actionName, ActionManager manager, List<? super AnAction> actions) {
    final AnAction action = manager.getAction(actionName);
    assert action != null : "Can not find action " + actionName;
    actions.add(action);
  }
}
