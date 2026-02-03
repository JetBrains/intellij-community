// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebugException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class ThrowDebugExceptionAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    try {
      throw new DebugException();
    }
    catch (DebugException ignored) {
    }
  }
}
