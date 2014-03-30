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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;

public class InvalidateCachesAction extends AnAction implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(ApplicationManager.getApplication().isRestartCapable() ? "Invalidate Caches / Restart..." : "Invalidate Caches...");
  }

  public void actionPerformed(AnActionEvent e) {
    final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    final boolean mac = Messages.canShowMacSheetPanel();
    boolean canRestart = app.isRestartCapable();
    
    String[] options = new String[canRestart ? 4 : 3];
    options[0] = canRestart ? "Invalidate and Restart" : "Invalidate and Exit";
    options[1] = mac ? "Cancel" : "Invalidate";
    options[2] = mac ? "Invalidate" : "Cancel";
    if (canRestart) {
      options[3] = "Just Restart";
    }

    int result = Messages.showDialog(e.getData(CommonDataKeys.PROJECT),
                                     "The caches will be invalidated and rebuilt on the next startup.\n" +
                                     "WARNING: Local History will be also cleared.\n\n" +
                                     "Would you like to continue?\n\n",
                                     "Invalidate Caches",
                                     options, 0,
                                     Messages.getWarningIcon());

    if (result == -1 || result == (mac ? 1 : 2)) {
      return;
    }
    
    if (result == 3) {
      app.restart(true);
      return;
    }

    FSRecords.invalidateCaches();
    if (result == 0) app.restart(true);
  }
}
