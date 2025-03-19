// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class VcsEditableTextComponent extends VcsLinkedTextComponent implements VcsEditableComponent {

  public VcsEditableTextComponent(@NotNull String text, @Nullable VcsLinkListener listener) {
    super(text, listener);
  }
}
