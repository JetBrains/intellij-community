// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

final class HierarchyScopeDescriptorProvider implements ScopeDescriptorProvider {

  @Override
  public ScopeDescriptor @NotNull [] getScopeDescriptors(@NotNull Project project,
                                                         @NotNull DataContext dataContext) {
    if (Comparing.strEqual(ToolWindowManager.getInstance(project).getActiveToolWindowId(), ToolWindowId.TODO_VIEW)) {
      return EMPTY;
    }
    return new ScopeDescriptor[]{new ClassHierarchyScopeDescriptor(project, dataContext)};
  }
}