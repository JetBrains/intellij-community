// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

final class NewWebDevelopmentGroup extends DefaultActionGroup implements NewFileActionWithCategory {
  @Override
  public @NotNull String getCategory() {
    return "Java";
  }
}
