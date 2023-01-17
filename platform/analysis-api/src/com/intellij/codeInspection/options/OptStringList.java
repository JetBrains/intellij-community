// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents editable sorted list of unique strings
 *
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
 * @param label label above the control
 * @param validator optional validator for content; can validate max-length or be something more complicated
 *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
 */
public record OptStringList(@Language("jvm-field-name") @NotNull String bindId,
                            @NotNull LocMessage label, @Nullable StringValidator validator) implements OptControl {
  @Override
  public @NotNull OptStringList prefix(@NotNull String bindPrefix) {
    return new OptStringList(bindPrefix + "." + bindId, label, validator);
  }
}
