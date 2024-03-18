// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A command that updates system options accessible via {@link com.intellij.codeInspection.options.OptionControllerProvider}.
 * Could be used, for example, to update inspection settings. 
 * 
 * @param options list of options to update
 */
@ApiStatus.Experimental
public record ModUpdateSystemOptions(@NotNull List<@NotNull ModifiedOption> options) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return options.isEmpty();
  }

  /**
   * A modified option record. Supported value types are boolean, int, long, double, String, enum, List&lt;String&gt;
   * 
   * @param bindId id of the option. Should be accessible via {@link com.intellij.codeInspection.options.OptionControllerProvider}
   * @param oldValue old value of the option
   * @param newValue new value of the option
   */
  public record ModifiedOption(@NotNull String bindId, @Nullable Object oldValue, @Nullable Object newValue) {}

  @Override
  public @NotNull ModCommand andThen(@NotNull ModCommand next) {
    if (next instanceof ModUpdateSystemOptions nextUpdate) {
      return new ModUpdateSystemOptions(ContainerUtil.concat(options, nextUpdate.options));
    }
    return ModCommand.super.andThen(next);
  }
}
