// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
public final class GenerateAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    Project project = Objects.requireNonNull(getEventProject(e));
    ListPopup popup =
      JBPopupFactory.getInstance().createActionGroupPopup(
        CodeInsightBundle.message("generate.list.popup.title"),
        wrapGroup(getGroup(), project, e),
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false);

    popup.showInBestPositionFor(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Project project = event.getProject();
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    boolean enabled = project != null && editor != null &&
                      !ActionGroupUtil.isGroupEmpty(getGroup(), event);
    if (event.isFromContextMenu()) {
      event.getPresentation().setEnabledAndVisible(enabled);
    }
    else {
      event.getPresentation().setEnabled(enabled);
    }
  }

  private static @NotNull DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE);
  }

  private static DefaultActionGroup wrapGroup(@NotNull DefaultActionGroup actionGroup,
                                              @NotNull Project project,
                                              @NotNull AnActionEvent event) {
    final DefaultActionGroup copy = new DefaultActionGroup();
    for (final AnAction action : actionGroup.getChildren(event.getActionManager())) {
      if (DumbService.isDumb(project) && !action.isDumbAware()) {
        continue;
      }

      if (action instanceof GenerateActionPopupTemplateInjector o) {
        final AnAction editTemplateAction = o.createEditTemplateAction(event.getDataContext());
        if (editTemplateAction != null) {
          copy.add(new GenerateWrappingGroup(action, editTemplateAction));
          continue;
        }
      }
      if (action instanceof DefaultActionGroup o) {
        copy.add(wrapGroup(o, project, event));
      }
      else {
        copy.add(action);
      }
    }
    return copy;
  }

  private static final class GenerateWrappingGroup extends ActionGroup {

    private final AnAction myAction;
    private final AnAction myEditTemplateAction;

    GenerateWrappingGroup(AnAction action, AnAction editTemplateAction) {
      myAction = action;
      myEditTemplateAction = editTemplateAction;
      copyFrom(action);
      setPopup(true);
      getTemplatePresentation().setPerformGroup(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return myAction.getActionUpdateThread();
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {myEditTemplateAction};
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DumbService.getInstance(Objects.requireNonNull(getEventProject(e)))
        .withAlternativeResolveEnabled(() -> myAction.actionPerformed(e));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myAction.update(e);
    }
  }
}