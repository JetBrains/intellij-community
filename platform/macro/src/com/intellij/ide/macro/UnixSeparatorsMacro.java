// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnixSeparatorsMacro extends Macro implements SecondQueueExpandMacro, MacroWithParams {
  @Override
  public @NotNull String getName() {
    return "UnixSeparators";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.unix.separators");
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext, String @NotNull ... args) throws ExecutionCancelledException {
    if (args.length == 1) {
      return FileUtil.toSystemIndependentName(args[0]);
    }
    return super.expand(dataContext, args);
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext) {
    return null;
  }
}
