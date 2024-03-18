// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class DefaultActionGroupWithDelegate extends DefaultActionGroup implements ActionWithDelegate<ActionGroup> {
  private final ActionGroup myDelegate;

  public DefaultActionGroupWithDelegate(ActionGroup delegate) {
    if (delegate instanceof ActionWithDelegate) {
      Object d = ((ActionWithDelegate<?>)delegate).getDelegate();
      if (d instanceof ActionGroup) {
        delegate = (ActionGroup)d;
      }
    }
    myDelegate = delegate;
  }

  @Override
  public @NotNull ActionGroup getDelegate() {
    return myDelegate;
  }
}
