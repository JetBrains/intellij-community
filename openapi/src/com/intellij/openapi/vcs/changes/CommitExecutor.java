package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public interface CommitExecutor {
  @NotNull
  Icon getActionIcon();

  @Nls
  String getActionText();

  @Nls
  String getActionDescription();

  @NotNull
  CommitSession createCommitSession();
}
