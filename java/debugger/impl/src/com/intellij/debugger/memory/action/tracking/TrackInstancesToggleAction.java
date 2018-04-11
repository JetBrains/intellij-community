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
package com.intellij.debugger.memory.action.tracking;

import com.intellij.debugger.memory.action.ActionUtil;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.tracking.TrackingType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

public class TrackInstancesToggleAction extends ToggleAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ReferenceType selectedClass = ActionUtil.getSelectedClass(e);
    if (selectedClass instanceof ArrayType) {
      e.getPresentation().setEnabled(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    ReferenceType selectedClass = ActionUtil.getSelectedClass(e);
    final Project project = e.getProject();
    if (project != null && selectedClass != null && !project.isDisposed()) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      return tracker.isTracked(selectedClass.name());
    }

    return false;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final ReferenceType selectedClass = ActionUtil.getSelectedClass(e);
    final Project project = e.getProject();
    if (selectedClass != null && project != null && !project.isDisposed()) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      boolean isAlreadyTracked = tracker.isTracked(selectedClass.name());

      if (isAlreadyTracked && !state) {
        tracker.remove(selectedClass.name());
      }

      if (!isAlreadyTracked && state) {
        tracker.add(selectedClass.name(), TrackingType.CREATION);
      }
    }
  }


}
