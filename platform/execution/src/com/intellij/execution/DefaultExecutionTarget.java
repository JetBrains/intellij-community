// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.IdeCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DefaultExecutionTarget extends ExecutionTarget {
  public static final ExecutionTarget INSTANCE = new DefaultExecutionTarget();

  private DefaultExecutionTarget() {
  }

  @Override
  public @NotNull String getId() {
    return "default_target";
  }

  @Override
  public @NotNull String getDisplayName() {
    return IdeCoreBundle.message("node.default");
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean canRun(@NotNull RunConfiguration configuration) {
    return true;
  }
}
