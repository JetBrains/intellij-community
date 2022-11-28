// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a custom control that can be rendered by some UI provides in non-specified way.
 * 
 * @param bindId ID to bind the custom control to.
 */
public record OptCustom(@NotNull String bindId) implements OptControl {
}
