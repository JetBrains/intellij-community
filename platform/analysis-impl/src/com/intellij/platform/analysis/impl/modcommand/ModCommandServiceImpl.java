// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.analysis.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  @NotNull
  public IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
  }

  @Override
  @Nullable
  public ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : null;
  }
}
