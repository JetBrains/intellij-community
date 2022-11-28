// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an edit box to enter a number
 * 
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be int or long
 * @param splitLabel label to display around the control
 * @param minValue minimal allowed value of the variable
 * @param maxValue maximal allowed value of the variable
 */
public record OptNumber(@NotNull String bindId, @NotNull LocMessage splitLabel, int minValue, int maxValue) implements OptControl {
}
