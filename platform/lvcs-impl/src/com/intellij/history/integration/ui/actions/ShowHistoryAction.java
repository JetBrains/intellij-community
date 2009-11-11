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

package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.history.integration.ui.views.HistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowHistoryAction extends LocalHistoryActionWithDialog {
  @Override
  protected void showDialog(Project p, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    HistoryDialog frame = f.isDirectory() ? new DirectoryHistoryDialog(p, gw, f) : new FileHistoryDialog(p, gw, f);
    frame.show();
  }

  @Override
  protected boolean isEnabled(LocalHistoryFacade vcs, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return f != null && gw.isVersioned(f) && (f.isDirectory() || gw.areContentChangesVersioned(f));
  }
}
