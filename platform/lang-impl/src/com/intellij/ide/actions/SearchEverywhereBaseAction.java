// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.actions.GotoActionBase.getInitialText;

public abstract class SearchEverywhereBaseAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public void update(final @NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    boolean hasContributors = hasContributors(dataContext);
    presentation.setEnabled((!requiresProject() || project != null) && hasContributors);
    presentation.setVisible(hasContributors);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean requiresProject() {
    return true;
  }

  protected boolean hasContributors(DataContext context){
    return true;
  }

  protected void showInSearchEverywherePopup(@NotNull String tabID,
                                             @NotNull AnActionEvent event,
                                             boolean useEditorSelection,
                                             boolean sendStatistics) {
    Project project = event.getProject();
    SearchEverywhereManager seManager = SearchEverywhereManager.getInstance(project);

    if (seManager.isShown()) {
      if (tabID.equals(seManager.getSelectedTabID())) {
        seManager.toggleEverywhereFilter();
      }
      else {
        seManager.setSelectedTabID(tabID);
        if (sendStatistics) {
          SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                                 SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(tabID),
                                                                 EventFields.InputEventByAnAction.with(event));
        }
      }
      return;
    }

    if (sendStatistics) {
      SearchEverywhereUsageTriggerCollector.DIALOG_OPEN.log(project, tabID, event);
    }
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
    String searchText = StringUtil.nullize(getInitialText(useEditorSelection, event).first);
    seManager.show(tabID, searchText, event);
  }
}
