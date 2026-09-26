// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows specific languages to override backspace unindent mode set in global editor preferences.
 * 
 * @see SmartBackspaceDisabler
 */
public abstract class BackspaceModeOverride {

  /** Use {@link BackspaceModeOverride#getBackspaceMode(PsiFile, SmartBackspaceMode)} instead */
  @ApiStatus.Obsolete
  public @NotNull SmartBackspaceMode getBackspaceMode(@NotNull SmartBackspaceMode modeFromSettings) {
    return modeFromSettings;
  }

  public @NotNull SmartBackspaceMode getBackspaceMode(@NotNull PsiFile file, SmartBackspaceMode mode) {
    return getBackspaceMode(mode);
  }
}
