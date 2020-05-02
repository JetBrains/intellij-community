// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public class DebugTasksViewManager extends TasksViewManager   {
  public DebugTasksViewManager(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getViewName() {
    return LangBundle.message("debug.view.title");
  }

  @Override
  protected Icon getContentIcon() {
    return AllIcons.Actions.StartDebugger;
  }
}
