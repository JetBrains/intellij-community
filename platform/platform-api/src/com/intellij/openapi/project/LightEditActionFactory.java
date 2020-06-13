// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static DumbAwareAction create(@NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleLightEditCompatibleAction(actionPerformed);
  }

  @NotNull
  public static DumbAwareAction create(@Nullable @NlsActions.ActionText String text,
                                       @NotNull Consumer<? super AnActionEvent> actionPerformed) {
    return new SimpleLightEditCompatibleAction(text, actionPerformed);
  }

  private static class SimpleLightEditCompatibleAction extends DumbAwareAction.SimpleDumbAwareAction implements LightEditCompatible {

    SimpleLightEditCompatibleAction(Consumer<? super AnActionEvent> actionPerformed) {
      super(actionPerformed);
    }

    SimpleLightEditCompatibleAction(@NlsActions.ActionText String text,
                                    Consumer<? super AnActionEvent> actionPerformed) {
      super(text, actionPerformed);
    }
  }
}
