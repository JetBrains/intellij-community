/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BitUtil;

import java.awt.event.InputEvent;
import java.io.File;

/**
* @author yole
*/
public class ReopenProjectAction extends AnAction implements DumbAware {
  private final String myProjectPath;
  private final String myProjectName;
  private boolean myIsRemoved = false;

  public ReopenProjectAction(final String projectPath, final String projectName, final String displayName) {
    myProjectPath = projectPath;
    myProjectName = projectName;

    final Presentation presentation = getTemplatePresentation();
    String text = projectPath.equals(displayName) ? FileUtil.getLocationRelativeToUserHome(projectPath) : displayName;
    presentation.setText(text, false);
    presentation.setDescription(projectPath);
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    //Force move focus to IdeFrame
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups();

    final int modifiers = e.getModifiers();
    final boolean forceOpenInNewFrame = BitUtil.isSet(modifiers, InputEvent.CTRL_MASK)
                                        || BitUtil.isSet(modifiers, InputEvent.SHIFT_MASK)
                                        || e.getPlace() == ActionPlaces.WELCOME_SCREEN;

    Project project = e.getProject();
    if (!new File(myProjectPath).exists()) {
      if (Messages.showDialog(project, "The path " + FileUtil.toSystemDependentName(myProjectPath) + " does not exist.\n" +
                                       "If it is on a removable or network drive, please make sure that the drive is connected.",
                                       "Reopen Project", new String[]{"OK", "&Remove From List"}, 0, Messages.getErrorIcon()) == 1) {
        myIsRemoved = true;
        RecentProjectsManager.getInstance().removePath(myProjectPath);
      }
      return;
    }
    RecentProjectsManagerBase.getInstanceEx().doOpenProject(myProjectPath, project, forceOpenInNewFrame);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(getProjectName(), false);
  }

  public String getProjectPath() {
    return myProjectPath;
  }

  public boolean isRemoved() {
    return myIsRemoved;
  }
  
  public String getProjectName() {
    final RecentProjectsManager mgr = RecentProjectsManager.getInstance();
    if (mgr instanceof RecentProjectsManagerBase) {
      return ((RecentProjectsManagerBase)mgr).getProjectName(myProjectPath);
    }
    return myProjectName;
  }
}
