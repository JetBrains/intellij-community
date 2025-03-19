// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class MarkSourceRootActionGroup extends ActionGroup {
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    //todo obtain compatible root types by module
    List<AnAction> actions = new ArrayList<>();
    for (JpsModuleSourceRootType<?> type : Arrays.asList(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE, 
                                                         JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE)) {
      actions.add(new MarkSourceRootAction(type));
    }
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
