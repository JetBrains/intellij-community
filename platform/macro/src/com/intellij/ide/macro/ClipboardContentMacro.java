// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;

public class ClipboardContentMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "ClipboardContent";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.clipboard.content");
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext) throws ExecutionCancelledException {
    return CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
  }
}
