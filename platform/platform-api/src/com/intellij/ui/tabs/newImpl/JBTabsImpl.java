// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
public class JBTabsImpl extends com.intellij.ui.tabs.impl.JBTabsImpl {
  public JBTabsImpl(@NotNull Project project) {
    super(project);
  }

  public JBTabsImpl(@Nullable Project project,
                    IdeFocusManager focusManager,
                    @NotNull Disposable parent) {
    super(project, focusManager, parent);
  }

  public JBTabsImpl(@Nullable Project project,
                    @NotNull ActionManager actionManager,
                    IdeFocusManager focusManager,
                    @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }
}
