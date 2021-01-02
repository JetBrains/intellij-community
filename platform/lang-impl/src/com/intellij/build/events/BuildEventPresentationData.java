// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public interface BuildEventPresentationData {
  @NotNull Icon getNodeIcon();

  @Nullable ExecutionConsole getExecutionConsole();

  @Nullable ActionGroup consoleToolbarActions();
}
