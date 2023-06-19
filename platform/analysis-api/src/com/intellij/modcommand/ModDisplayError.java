// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * A command that displays an error message.
 * 
 * @param errorMessage localized error message to display
 */
public record ModDisplayError(@NlsContexts.Tooltip @NotNull String errorMessage) implements ModCommand {
}
