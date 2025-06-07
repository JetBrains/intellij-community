// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;

public final class CompilerContextMakeMacro extends Macro {

  public static final DataKey<Boolean> COMPILER_CONTEXT_MAKE_KEY = DataKey.create("COMPILER_CONTEXT_MAKE");

  @Override
  public @NotNull String getName() {
    return "IsMake";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.compiler.context.is.make");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    Boolean make = dataContext.getData(COMPILER_CONTEXT_MAKE_KEY);
    return make != null ? make.toString() : null;
  }
}
