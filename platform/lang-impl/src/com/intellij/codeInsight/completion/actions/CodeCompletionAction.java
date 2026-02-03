// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeCompletionAction extends BaseCodeCompletionAction implements LightEditCompatible, ActionRemoteBehaviorSpecification {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    invokeCompletion(e, CompletionType.BASIC, 1);
  }

  @Override
  @ApiStatus.Internal
  public final @Nullable ActionRemoteBehavior getBehavior() {
    return getCompletionBehavior();
  }

  @ApiStatus.Internal
  public static @Nullable ActionRemoteBehavior getCompletionBehavior() {
    if (Registry.is("remdev.completion.on.frontend")) {
      return ActionRemoteBehavior.FrontendOnly;
    }
    else {
      return null;
    }
  }
}
