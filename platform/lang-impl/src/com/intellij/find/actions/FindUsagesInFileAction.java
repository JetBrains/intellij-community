// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import kotlin.ReplaceWith;
import org.jetbrains.annotations.NotNull;

public final class FindUsagesInFileAction {

  /**
   * @deprecated Use #{@link FindUsagesAction#updateFindUsagesAction(AnActionEvent)}
   */
  @Deprecated
  public static void updateFindUsagesAction(@NotNull AnActionEvent event) {
    FindUsagesAction.updateFindUsagesAction(event);
  }
}