// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class BackspaceSelectionLoggingHandler extends SelectionDeleteLoggingHandlerBase {
  public BackspaceSelectionLoggingHandler(EditorActionHandler originalHandler) {
    super(originalHandler, TypingEventsLogger.SelectionDeleteAction.BACKSPACE);
  }
}
