// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();

  @Nullable
  public abstract CommandToken startCommand(@Nullable Project project, @Nls String name, @Nullable Object groupId, @NotNull UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void finishCommand(@NotNull final CommandToken command, @Nullable Throwable throwable);
}
