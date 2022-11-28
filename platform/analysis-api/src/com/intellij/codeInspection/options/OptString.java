// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an edit box to enter a string
 *
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be string
 * @param splitLabel label to display around the control
 * @param width width of the control in approximate number of characters; if -1 then it will be determined automatically
 * @param validator optional validator for content; can validate max-length or be something more complicated
 *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
 */
public record OptString(@NotNull String bindId, @NotNull LocMessage splitLabel, @Nullable StringValidator validator, int width) implements OptControl {
}
