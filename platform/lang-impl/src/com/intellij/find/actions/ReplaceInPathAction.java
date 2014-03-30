
/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.find.actions;

import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class ReplaceInPathAction extends AnAction {
  { // enabled in modal content for find in path <-> replace in path modal dialog transition
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    ReplaceInProjectManager replaceManager = ReplaceInProjectManager.getInstance(project);
    if (!replaceManager.isEnabled()) {
      FindInPathAction.showNotAvailableMessage(e, project);
      return;
    }

    replaceManager.replaceInProject(dataContext);
  }

  @Override
  public void update(AnActionEvent event){
    FindInPathAction.doUpdate(event);
  }
}
