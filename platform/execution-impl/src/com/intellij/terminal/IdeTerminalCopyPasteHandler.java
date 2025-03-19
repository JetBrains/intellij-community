// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.openapi.ide.CopyPasteManager;
import com.jediterm.terminal.DefaultTerminalCopyPasteHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

@ApiStatus.Internal
public final class IdeTerminalCopyPasteHandler extends DefaultTerminalCopyPasteHandler {

  @Override
  protected void setSystemClipboardContents(@NotNull String text) {
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }
}
