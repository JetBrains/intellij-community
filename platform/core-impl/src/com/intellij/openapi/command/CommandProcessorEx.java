// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();

  @Nullable
  public abstract CommandToken startCommand(@Nullable Project project, @NlsContexts.Command String name, @Nullable Object groupId, @NotNull UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void finishCommand(@NotNull final CommandToken command, @Nullable Throwable throwable);
}
