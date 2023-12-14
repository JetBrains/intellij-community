// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.TextWithMnemonic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class WeighingNewActionGroup extends WeighingActionGroup implements DumbAware {
  private ActionGroup myDelegate;

  @Override
  public @NotNull ActionGroup getDelegate() {
    if (myDelegate == null) {
      myDelegate = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
    }
    return myDelegate;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Supplier<TextWithMnemonic> prev = e.getPresentation().getTextWithPossibleMnemonic();
    super.update(e);
    if (e.getPresentation().getTextWithPossibleMnemonic() != prev) {
      e.getPresentation().setTextWithMnemonic(prev);
    }
  }

  @Override
  protected boolean shouldBeChosenAnyway(@NotNull AnAction action) {
    final Class<? extends AnAction> aClass = action.getClass();
    return aClass == CreateFileAction.class || aClass == CreateDirectoryOrPackageAction.class ||
           "NewModuleInGroupAction".equals(aClass.getSimpleName());
  }
}
