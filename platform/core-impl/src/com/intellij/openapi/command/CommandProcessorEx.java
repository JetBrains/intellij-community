// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();

  public abstract @Nullable CommandToken startCommand(@Nullable Project project, @NlsContexts.Command String name, @Nullable Object groupId, @NotNull UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void finishCommand(final @NotNull CommandToken command, @Nullable Throwable throwable);
}
