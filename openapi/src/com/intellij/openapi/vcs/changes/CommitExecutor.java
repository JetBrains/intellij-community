package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public interface CommitExecutor {
  Icon getActionIcon();

  @Nls
  String getActionText();

  @Nls
  String getActionDescription();

  @Nullable
  JComponent getAdditionalConfigurationUI();

  void execute(Collection<Change> changes, String commitMessage);
}
