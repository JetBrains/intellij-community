// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.CommonBundle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.OpenProjectTaskKt;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem;
import com.intellij.util.BitUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.awt.event.InputEvent;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReopenProjectAction extends AnAction implements DumbAware, LightEditCompatible {
  private final String myProjectPath;
  private final String myProjectName;
  private boolean myIsRemoved = false;
  private @Nullable ProjectGroup myProjectGroup;

  public ReopenProjectAction(@NotNull @SystemIndependent String projectPath, @NlsSafe String projectName, @NlsSafe String displayName) {
    myProjectPath = projectPath;
    myProjectName = projectName;

    Presentation presentation = getTemplatePresentation();
    String text = projectPath.equals(displayName) ? FileUtil.getLocationRelativeToUserHome(projectPath) : displayName;
    presentation.setText(text, false);
    presentation.setDescription(FileUtil.toSystemDependentName(projectPath));
    if (Strings.isEmpty(text)) {
      Logger.getInstance(ReopenProjectAction.class).error(
        String.format("Empty action text for projectName='%s' displayName='%s' path='%s'", projectName, displayName, projectPath));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // force move focus to IdeFrame
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups();

    Project project = e.getProject();
    Path file = Path.of(myProjectPath).normalize();
    if (!Files.exists(file)) {
      if (Messages.showDialog(project, IdeBundle
                                .message("message.the.path.0.does.not.exist.maybe.on.remote", FileUtil.toSystemDependentName(myProjectPath)),
                              IdeBundle.message("dialog.title.reopen.project"), new String[]{CommonBundle.getOkButtonText(), IdeBundle.message("button.remove.from.list")}, 0, Messages.getErrorIcon()) == 1) {
        myIsRemoved = true;
        RecentProjectsManager.getInstance().removePath(myProjectPath);
      }
      return;
    }


    OpenProjectTask options = OpenProjectTaskKt.OpenProjectTask(builder -> {
      builder.setProjectToClose(project);
      int modifiers = e.getModifiers();
      builder.setForceOpenInNewFrame(BitUtil.isSet(modifiers, InputEvent.CTRL_MASK)
                                       || BitUtil.isSet(modifiers, InputEvent.SHIFT_MASK)
                                       || ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())
                                       || LightEdit.owns(project));
      builder.setRunConfigurators(true);
      return Unit.INSTANCE;
    });
    RecentProjectItem.Companion.openProjectAndLogRecent(file, options, myProjectGroup);
  }

  public @SystemIndependent String getProjectPath() {
    return myProjectPath;
  }

  public boolean isRemoved() {
    return myIsRemoved;
  }

  public @NlsSafe String getProjectName() {
    RecentProjectsManager manager = RecentProjectsManager.getInstance();
    if (manager instanceof RecentProjectsManagerBase) {
      return ((RecentProjectsManagerBase)manager).getProjectName(myProjectPath);
    }
    return myProjectName;
  }

  public @NlsSafe @Nullable String getProjectNameToDisplay() {
    final RecentProjectsManager mgr = RecentProjectsManager.getInstance();
    String displayName = mgr instanceof RecentProjectsManagerBase
                         ? ((RecentProjectsManagerBase)mgr).getDisplayName(myProjectPath)
                         : null;
    return displayName != null ? displayName : getProjectName();
  }

  @Override
  public @NlsActions.ActionText @Nullable String getTemplateText() {
    return IdeBundle.message("action.ReopenProject.reopen.project.text");
  }

  public void setProjectGroup(@Nullable ProjectGroup projectGroup) {
    myProjectGroup = projectGroup;
  }
}
