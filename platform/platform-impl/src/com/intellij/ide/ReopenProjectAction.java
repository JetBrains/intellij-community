/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import java.awt.event.InputEvent;

/**
* @author yole
*/
public class ReopenProjectAction extends AnAction implements DumbAware {
  private final String myProjectPath;
  private final String myProjectName;

  public ReopenProjectAction(final String projectPath, final String projectName) {
    myProjectPath = projectPath;
    myProjectName = projectName;

    String _text = projectPath;
    if (projectName != null) {
      _text = String.format("%s", projectName);
    }

    final Presentation presentation = getTemplatePresentation();
    presentation.setText(_text, false);
    presentation.setDescription(projectPath);
  }


  public void actionPerformed(AnActionEvent e) {
    final int modifiers = e.getModifiers();
    final boolean forceOpenInNewFrame = (modifiers & InputEvent.CTRL_MASK) != 0 || (modifiers & InputEvent.SHIFT_MASK) != 0;
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    RecentProjectsManagerBase.getInstance().doOpenProject(myProjectPath, project, forceOpenInNewFrame);
  }

  public String getProjectPath() {
    return myProjectPath;
  }
  
  public boolean hasPath() {
    return myProjectPath.equals(myProjectName);
  }

  public String getProjectName() {
    return myProjectName;
  }
}
