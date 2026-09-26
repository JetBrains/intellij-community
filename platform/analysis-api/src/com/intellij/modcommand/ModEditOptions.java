// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A command that allows to interactively edit the option container and issue a next command after the editing is done.
 * Does nothing if the editing was cancelled by user
 *
 * @param title user-readable title to display in UI
 * @param containerSupplier a stateless supplier capable of producing identical options containers to edit. The supplier is necessary,
 *                          as the command must be immutable, so we cannot simply store a container inside the command.
 * @param canUseDefaults if true, then it's allowed to skip the interactive editing in the batch mode, accepting the
 *                       default container state. If false, the command will not be available in the batch mode.
 * @param nextCommand a function that generates the next command to execute after the editing is finished.
 *                    The function will be executed in background read action.
 * @param <T> type of the option container. Must have properly defined {@link OptionContainer#getOptionsPane()} method, so
 *           it should be possible to generate the editing UI.
 */
public record ModEditOptions<T extends OptionContainer>(
  @NotNull @NlsContexts.PopupTitle String title,
  @NotNull Supplier<T> containerSupplier,
  boolean canUseDefaults,
  @NotNull Function<? super T, ? extends @NotNull ModCommand> nextCommand
) implements ModCommand {
  @Override
  public @NotNull ModCommand andThen(@NotNull ModCommand next) {
    return next.isEmpty() ? this : new ModEditOptions<>(title, containerSupplier, canUseDefaults, nextCommand.andThen(mc -> mc.andThen(next)));
  }

  /**
   * @param options map of options to apply (keys = bindID; values = the corresponding value)
   * @return the command that should be executed in response to the applied options.
   */
  @TestOnly
  public @NotNull ModCommand applyOptions(@NotNull Map<String, Object> options) {
    T container = containerSupplier.get();
    OptionController controller = container.getOptionController();
    options.forEach(controller::setOption);
    return nextCommand.apply(container);
  } 
}
