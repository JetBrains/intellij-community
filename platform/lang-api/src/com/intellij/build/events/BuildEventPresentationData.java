// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

@Experimental
public interface BuildEventPresentationData {

  @NotNull Icon getNodeIcon();

  @Nullable ExecutionConsole getExecutionConsole();

  @Nullable ActionGroup consoleToolbarActions();
}
