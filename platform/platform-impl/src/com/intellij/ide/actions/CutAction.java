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

import com.intellij.ide.CutProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class CutAction extends AnAction implements DumbAware {
  public CutAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CutProvider provider = PlatformDataKeys.CUT_PROVIDER.getData(dataContext);
    if (provider == null) {
      return;
    }
    provider.performCut(dataContext);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CutProvider provider = PlatformDataKeys.CUT_PROVIDER.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(project != null && project.isOpen() && provider != null && provider.isCutEnabled(dataContext));
    if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
      presentation.setVisible(provider.isCutVisible(dataContext));
    }
    else {
      presentation.setVisible(true);
    }
  }
}
