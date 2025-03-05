// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditActionFactory {
  private LightEditActionFactory() {
  }

  public static @NotNull DumbAwareAction create(@NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleLightEditCompatibleAction(actionPerformed);
  }

  public static @NotNull DumbAwareAction create(@Nullable @NlsActions.ActionText String text,
                                                @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleLightEditCompatibleAction(text, actionPerformed);
  }

  private static final class SimpleLightEditCompatibleAction extends DumbAwareAction.SimpleDumbAwareAction implements LightEditCompatible {

    SimpleLightEditCompatibleAction(Consumer<? super AnActionEvent> actionPerformed) {
      super(actionPerformed);
    }

    SimpleLightEditCompatibleAction(@NlsActions.ActionText String text,
                                    Consumer<? super AnActionEvent> actionPerformed) {
      super(text, actionPerformed);
    }
  }
}
