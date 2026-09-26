// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the {@link ConsoleView#createConsoleActions() console actions} customization.
 */
public abstract class ConsoleActionsPostProcessor {

  public static final ExtensionPointName<ConsoleActionsPostProcessor> EP_NAME = ExtensionPointName.create("com.intellij.consoleActionsPostProcessor");
  
  /**
   * Allows to adjust actions to use within the given console instance.
   * <p/>
   * {@code 'Adjust'} here stands for 'add', 'remove', 'change order' etc.
   *
   * @param console     console instance which actions are being post-processed
   * @param actions     console actions that will be used by default
   * @return            actions to use within the given console instance (given actions may be returned by default)
   */
  public AnAction @NotNull [] postProcess(@NotNull ConsoleView console, AnAction @NotNull [] actions) {
    return actions;
  }

  public AnAction @NotNull [] postProcessPopupActions(@NotNull ConsoleView console, AnAction @NotNull [] actions) {
    return actions;
  }
}
