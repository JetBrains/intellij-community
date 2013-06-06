package com.intellij.execution.ui;

import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to customize execution console presentation.
 * For example, it's possible to add extra Content instances to a layout.
 *
 * @author Sergey Simonchik
 */
public interface LayoutAwareExecutionConsole {

  /**
   * Returns {@code Content} instance for this console.
   *
   * @param ui {@link RunnerLayoutUi} instance
   * @return {@link Content} instance for this console
   */
  @NotNull
  Content buildContent(@NotNull RunnerLayoutUi ui);
}
