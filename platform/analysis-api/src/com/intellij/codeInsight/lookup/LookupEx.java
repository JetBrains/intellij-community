// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

public interface LookupEx extends Lookup {
  boolean performGuardedChange(Runnable change);

  void setCurrentItem(LookupElement item);

  Component getComponent();

  void showElementActions(@Nullable InputEvent event);

  void hideLookup(boolean explicitly);

  @NotNull
  LookupPresentation getPresentation();

  /**
   * Allows modifying the visual presentation of the lookup (the size, it's position, item ordering).
   * Should be set before lookup is shown, for example in {@link LookupManagerListener#activeLookupChanged(Lookup, Lookup)}.
   */
  @ApiStatus.Experimental
  void setPresentation(@NotNull LookupPresentation presentation);
}
