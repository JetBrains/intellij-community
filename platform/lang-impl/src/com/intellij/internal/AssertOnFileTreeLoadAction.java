// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

public class AssertOnFileTreeLoadAction extends ToggleAction {

  private static final Key<Boolean> KEY = Key.create("assertion.file.tree.load.is.set");

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;
    
    return project.getUserData(KEY) == Boolean.TRUE;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;
    if (state) {
      PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, project);
      project.putUserData(KEY, Boolean.TRUE);
    } else {
      PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, project);
      project.putUserData(KEY, null);
    }
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
