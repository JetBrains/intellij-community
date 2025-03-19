// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public interface ChangeNotes {
  void show(@Nullable @NlsContexts.DialogMessage String text);
}