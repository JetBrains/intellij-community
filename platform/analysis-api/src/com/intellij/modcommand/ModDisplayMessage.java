// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * A command that displays an message.
 *
 * @param messageText localized message to display
 * @param kind message kind
 */
public record ModDisplayMessage(@NlsContexts.Tooltip @NotNull String messageText, @NotNull MessageKind kind) implements ModCommand {
  public enum MessageKind {
    /**
     * Informational message
     */
    INFORMATION,

    /**
     * Error message
     */
    ERROR
  }
}
