/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.util.PlatformUtils;

/**
 * @author yole
 */
public class NewDummyProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = projectManager.newProject("dummy", PathManager.getConfigPath() + "/dummy.ipr", true, false);
    if (project == null) return;
    projectManager.openProject(project);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible("Platform".equals(System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)));
  }
}