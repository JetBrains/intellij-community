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

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class StopAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  public void actionPerformed(final AnActionEvent e) {
    final ProcessHandler processHandler = getProcessHandler(e);
    if(processHandler == null) return;
    if(processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    } else {
      processHandler.destroyProcess();
    }
  }

  public void update(final AnActionEvent e) {
    final ProcessHandler processHandler = getProcessHandler(e);
    boolean enable = processHandler != null && !processHandler.isProcessTerminating() && !processHandler.isProcessTerminated();
    e.getPresentation().setEnabled(enable);
  }

  @Nullable
  private static ProcessHandler getProcessHandler(final AnActionEvent e) {
    final RunContentDescriptor contentDescriptor = RunContentManager.RUN_CONTENT_DESCRIPTOR.getData(e.getDataContext());
    final ProcessHandler processHandler;
    if (contentDescriptor != null) {
      // toolwindow case
      processHandler = contentDescriptor.getProcessHandler();
    }
    else {
      // main menu toolbar
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      final RunContentDescriptor selectedContent = project == null? null : ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
      processHandler = selectedContent == null? null : selectedContent.getProcessHandler();
    }
    return processHandler;
  }
}
