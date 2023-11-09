// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.options.OptionController;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A command that updates the inspection options in the active profile and scope
 * 
 * @param options list of options to update
 */
public record ModUpdateInspectionOptions(@NotNull List<@NotNull ModifiedInspectionOption> options) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return options.isEmpty();
  }

  /**
   * A modified option record
   * 
   * @param bindId id of the option. Should be accessible via {@link OptionController}
   *               starting from current inspection profile. Usually, something like
   *               &lt;InspectionShortName&gt;.options.&lt;optionId&gt;
   * @param oldValue old value of the option
   * @param newValue new value of the option
   */
  public record ModifiedInspectionOption(@NotNull String bindId, @NotNull Object oldValue, @NotNull Object newValue) {}
}
