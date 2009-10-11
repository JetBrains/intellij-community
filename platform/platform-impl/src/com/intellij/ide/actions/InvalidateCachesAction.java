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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;

/**
 * @author yole
 */
public class InvalidateCachesAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    FSRecords.invalidateCaches();
    final Application app = ApplicationManager.getApplication();
    if (app.isRestartCapable()) {
      int rc = Messages.showYesNoDialog(e.getData(PlatformDataKeys.PROJECT),
                                        "The caches have been invalidated and will be rebuilt on the next startup. Would you like to restart " +
                                        ApplicationNamesInfo.getInstance().getFullProductName() + " now?",
                                        "Invalidate Caches", Messages.getInformationIcon());
      if (rc == 0) {
        app.restart();
      }
    }
    else {
      Messages.showMessageDialog(e.getData(PlatformDataKeys.PROJECT),
                                 "The caches have been invalidated and will be rebuilt on the next startup",
                                 "Invalidate Caches", Messages.getInformationIcon());
    }
  }
}
