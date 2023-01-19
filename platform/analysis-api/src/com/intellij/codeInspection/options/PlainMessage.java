// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a message which is already localized
 * 
 * @param label already localized label
 */
public record PlainMessage(@NotNull @Nls String label) implements LocMessage {
}
