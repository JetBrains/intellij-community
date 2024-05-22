// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class OsUserMacro extends Macro {
  @NotNull
  @Override
  public String getName() {
    return "OSUser";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeCoreBundle.message("macro.os.user");
  }

  @Nullable
  @Override
  public String expand(@NotNull DataContext dataContext) {
    return SystemProperties.getUserName();
  }
}