package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public interface CommitSession {
  @Nullable
  JComponent getAdditionalConfigurationUI();

  boolean canExecute(Collection<Change> changes, String commitMessage);
  void execute(Collection<Change> changes, String commitMessage);
  void executionCanceled();
}
