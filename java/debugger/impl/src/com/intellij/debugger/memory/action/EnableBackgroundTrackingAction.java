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
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.component.InstancesTracker;

public class EnableBackgroundTrackingAction extends ToggleAction {

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && !project.isDisposed() && InstancesTracker.getInstance(project).isBackgroundTrackingEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null && !project.isDisposed()) {
      InstancesTracker.getInstance(project).setBackgroundTackingEnabled(state);
    }
  }
}
