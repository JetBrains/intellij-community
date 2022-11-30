// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a two-state check-box (checked or unchecked)
 *
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be boolean
 * @param label label to display next to a checkbox
 * @param children optional list of children controls to display next to checkbox. They are disabled if checkbox is unchecked
 */
public record OptCheckbox(@NotNull String bindId, @NotNull LocMessage label, @NotNull List<@NotNull OptComponent> children) implements OptControl {
}
