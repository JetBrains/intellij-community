// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DefaultExecutionTarget extends ExecutionTarget {
  public static final ExecutionTarget INSTANCE = new DefaultExecutionTarget();

  private DefaultExecutionTarget() {
  }

  @NotNull
  @Override
  public String getId() {
    return "default_target";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return IdeBundle.message("node.default");
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
