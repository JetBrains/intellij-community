// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;

import static com.intellij.openapi.project.ProjectCoreUtil.theOnlyOpenProject;

public class JavaDebuggerSupport {
  /** @deprecated This method is an unreliable hack, find another way to locate a project instance. */
  @Deprecated(forRemoval = true)
  public static Project getContextProjectForEditorFieldsInDebuggerConfigurables() {
    //todo[nik] improve
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null) {
        return project;
      }
    }
    Project project = theOnlyOpenProject();
    return project != null ? project : ProjectManager.getInstance().getDefaultProject();
  }
}
