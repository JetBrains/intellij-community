package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public interface CommitSession {

  /**
   * @deprecated Since version 7.0, {@link #getAdditionalConfigurationUI(java.util.Collection, String)} is called instead
   */
  @Nullable
  JComponent getAdditionalConfigurationUI();

  @Nullable
  JComponent getAdditionalConfigurationUI(Collection<Change> changes, String commitMessage);

  boolean canExecute(Collection<Change> changes, String commitMessage);
  void execute(Collection<Change> changes, String commitMessage);
  void executionCanceled();
}
