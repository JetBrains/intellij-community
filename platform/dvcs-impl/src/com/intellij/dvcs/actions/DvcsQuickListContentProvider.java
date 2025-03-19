// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.actions;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.actions.VcsQuickListContentProvider;
import com.intellij.openapi.vcs.actions.VcsQuickListPopupAction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class DvcsQuickListContentProvider implements VcsQuickListContentProvider {

  @Override
  public @Nullable List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs,
                                                @NotNull AnActionEvent event) {
    if (activeVcs == null || !replaceVcsActionsFor(activeVcs, event.getDataContext())) return null;

    ActionGroup vcsGroup = ObjectUtils.tryCast(CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_OPERATIONS_POPUP),
                                               ActionGroup.class);
    if (vcsGroup == null) return null;

    UpdateSession updateSession = event.getUpdateSession();
    ActionGroup vcsAwareGroup = ObjectUtils.tryCast(ContainerUtil.find(updateSession.children(vcsGroup), action -> {
      return action instanceof VcsQuickListPopupAction.VcsAware ||
             action instanceof CustomisedActionGroup o && o.getDelegate() instanceof VcsQuickListPopupAction.VcsAware;
    }), ActionGroup.class);
    if (vcsAwareGroup == null) return null;

    final List<AnAction> actions = new ArrayList<>();
    ContainerUtil.addAll(actions, updateSession.children(vcsAwareGroup));
    customizeActions(ActionManager.getInstance(), actions);
    return actions;
  }

  protected void customizeActions(@NotNull ActionManager manager, @NotNull List<? super AnAction> actions) {
    List<AnAction> providerActions = collectVcsSpecificActions(manager);
    actions.removeAll(providerActions);
    actions.add(Separator.getInstance());
    actions.addAll(providerActions);
  }

  protected abstract @NonNls @NotNull String getVcsName();

  protected abstract List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager);

  @Override
  public boolean replaceVcsActionsFor(@NotNull AbstractVcs activeVcs, @NotNull DataContext dataContext) {
    return getVcsName().equals(activeVcs.getName());
  }

  protected static void add(String actionName, ActionManager manager, List<? super AnAction> actions) {
    final AnAction action = manager.getAction(actionName);
    assert action != null : "Can not find action " + actionName;
    actions.add(action);
  }
}
