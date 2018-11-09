// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public class StoreDefaultLayoutAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    Project project = e.getProject();
    if(project==null){
      return;
    }
    DesktopLayout layout=ToolWindowManagerEx.getInstanceEx(project).getLayout();
    WindowManagerEx.getInstanceEx().setLayout(layout);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(event.getProject() != null);
  }
}
