// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a column for {@link OptTable} that contains strings
 *
 * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
 * @param name      column name
 * @param validator optional validator for content; can validate max-length or be something more complicated
 *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
 */
public record OptTableColumn(@Language("jvm-field-name") @NotNull String bindId,
                             @NotNull LocMessage name, @Nullable StringValidator validator) implements OptControl {
  @Override
  public @NotNull OptTableColumn prefix(@NotNull String bindPrefix) {
    return new OptTableColumn(bindPrefix + "." + bindId, name, validator);
  }
}
