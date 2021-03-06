// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapping class around {@code LightEditService} methods.
 */
public final class LightEdit {
  private LightEdit() {
  }

  @Contract("null -> false")
  public static boolean owns(@Nullable Project project) {
    return project instanceof LightEditCompatible;
  }

  public static boolean isActionCompatible(@NotNull AnAction action) {
    return (action instanceof ActionGroup) && action.isDumbAware()
           || action instanceof LightEditCompatible;
  }

}
