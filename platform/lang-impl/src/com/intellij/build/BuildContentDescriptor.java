// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public final class BuildContentDescriptor extends RunContentDescriptor {
  private boolean activateToolWindowWhenFailed = true;
  private @NotNull ThreeState myNavigateToError = ThreeState.UNSURE;

  public BuildContentDescriptor(@Nullable ExecutionConsole executionConsole,
                                @Nullable ProcessHandler processHandler,
                                @NotNull JComponent component,
                                @BuildEventsNls.Title String displayName) {
    super(executionConsole, processHandler, component, displayName);
  }

  public boolean isActivateToolWindowWhenFailed() {
    return activateToolWindowWhenFailed;
  }

  public void setActivateToolWindowWhenFailed(boolean activateToolWindowWhenFailed) {
    this.activateToolWindowWhenFailed = activateToolWindowWhenFailed;
  }

  /**
   * @see DefaultBuildDescriptor#isNavigateToError()
   */
  public @NotNull ThreeState isNavigateToError() {
    return myNavigateToError;
  }

  public void setNavigateToError(@NotNull ThreeState navigateToError) {
    myNavigateToError = navigateToError;
  }
}
