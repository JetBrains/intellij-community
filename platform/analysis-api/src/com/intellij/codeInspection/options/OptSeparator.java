// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.Nullable;

/**
 * Separator (horizontal line), optionally with a label
 * 
 * @param label to use
 */
public record OptSeparator(@Nullable LocMessage label) implements OptComponent {
}
