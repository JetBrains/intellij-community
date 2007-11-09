package com.intellij.openapi.command;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Nls;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();

  @Nullable
  public abstract Object startCommand(Project project, @Nls String name, Object groupId, UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void finishCommand(Project project, final Object command, Throwable throwable);
}
