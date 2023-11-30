// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextHelpAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  private final String myHelpID;

  public ContextHelpAction() {
    this(null);
  }

  public ContextHelpAction(@NonNls @Nullable String helpID) {
    myHelpID = helpID;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final String helpId = getHelpId(dataContext);
    if (helpId != null) {
      HelpManager.getInstance().invokeHelp(helpId);
    }
  }

  protected @Nullable String getHelpId(DataContext dataContext) {
    return myHelpID != null ? myHelpID : PlatformCoreDataKeys.HELP_ID.getData(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    if (!ApplicationInfo.contextHelpAvailable()) {
      presentation.setVisible(false);
      return;
    }

    if (ActionPlaces.isMainMenuOrActionSearch(event.getPlace())) {
      DataContext dataContext = event.getDataContext();
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