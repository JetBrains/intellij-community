// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents editable two-column table of strings; strings in the left column are unique and sorted
 *
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be {@code Map<String, String>}.
 * @param label label above the control
 * @param keyValidator optional validator for keys column
 * @param valueValidator optional validator for values column
 */
public record OptMap(@NotNull String bindId, @NotNull LocMessage label, 
                     @Nullable StringValidator keyValidator, @Nullable StringValidator valueValidator) implements OptControl {
}
