// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.ide.SmartSelectProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class SmartUnSelect extends SmartSelect implements ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SmartSelectProvider provider = getProvider(e.getDataContext());
    assert provider != null;
    //noinspection unchecked
    provider.decreaseSelection(provider.getSource(e.getDataContext()));
  }

  @Override
  protected boolean isIncreasing() {
    return false;
  }
}
