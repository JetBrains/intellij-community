// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;

public final class GotoClassAction extends SearchEverywhereBaseAction implements DumbAware {

  public GotoClassAction() {
    //we need to change the template presentation to show the proper text for the action in Settings | Keymap
    Presentation p = getTemplatePresentation();
    p.setText(IdeBundle.messagePointer("go.to.class.title.prefix", GotoClassPresentationUpdater.getActionTitle() + "..."));
    p.setDescription(IdeBundle.messagePointer("go.to.class.action.description", StringUtil.join(GotoClassPresentationUpdater.getElementKinds(), "/")));
    addTextOverride(ActionPlaces.MAIN_MENU, () -> GotoClassPresentationUpdater.getActionTitle() + "...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e = SearchFieldStatisticsCollector.wrapEventWithActionStartData(e);
    Project project = e.getProject();
    if (project == null) return;

    boolean dumb = DumbService.isDumb(project);
    if (!dumb || isModelDumbAware(e)) {
      String tabID = ClassSearchEverywhereContributor.class.getSimpleName();
      showInSearchEverywherePopup(tabID, e, true, true);
    }
    else {
      invokeGoToFile(project, e, this);
    }
  }

  private static boolean isModelDumbAware(AnActionEvent e) {
    return new GotoClassModel2(e.getRequiredData(CommonDataKeys.PROJECT)).isDumbAware();
  }

  static void invokeGoToFile(@NotNull Project project, @NotNull AnActionEvent e, @NotNull AnAction failedAction) {
    String actionTitle = StringUtil.trimEnd(ObjectUtils.notNull(
      e.getPresentation().getText(), GotoClassPresentationUpdater.getActionTitle()), "...");
    String message = IdeBundle.message("go.to.class.dumb.mode.message", actionTitle);
    DumbService.getInstance(project).showDumbModeNotificationForAction(message, ActionManager.getInstance().getId(failedAction));
    AnAction action = ActionManager.getInstance().getAction(GotoFileAction.ID);
    InputEvent event = ActionCommand.getInputEvent(GotoFileAction.ID);
    Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    ActionManager.getInstance().tryToExecute(action, event, component, e.getPlace(), true);
  }

  @Override
  protected boolean hasContributors(@NotNull DataContext dataContext) {
    return !ChooseByNameRegistry.getInstance().getClassModelContributorList().isEmpty();
  }
}