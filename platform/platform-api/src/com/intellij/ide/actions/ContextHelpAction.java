// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextHelpAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  private final String myHelpID;

  public ContextHelpAction() {
    this(null);
  }

  public ContextHelpAction(@Nullable String helpID) {
    myHelpID = helpID;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var dataContext = e.getDataContext();
    var helpId = getHelpId(dataContext);
    if (helpId != null) {
      HelpManager.getInstance().invokeHelp(helpId);
    }
  }

  protected @Nullable String getHelpId(DataContext dataContext) {
    return myHelpID != null ? myHelpID : PlatformCoreDataKeys.HELP_ID.getData(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    var presentation = event.getPresentation();
    if (ActionPlaces.isMainMenuOrActionSearch(event.getPlace())) {
      var dataContext = event.getDataContext();
      presentation.setEnabled(getHelpId(dataContext) != null);
    }
    else {
      presentation.setIcon(AllIcons.Actions.Help);
      presentation.setText(CommonBundle.getHelpButtonText());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
