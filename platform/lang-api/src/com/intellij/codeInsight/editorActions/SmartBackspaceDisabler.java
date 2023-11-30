// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NotNull;

public class SmartBackspaceDisabler extends BackspaceModeOverride {
  @Override
  public @NotNull SmartBackspaceMode getBackspaceMode(@NotNull SmartBackspaceMode modeFromSettings) {
    return modeFromSettings == SmartBackspaceMode.AUTOINDENT ? SmartBackspaceMode.INDENT : modeFromSettings;
  }
}
