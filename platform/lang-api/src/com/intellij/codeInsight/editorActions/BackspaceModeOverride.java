// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NotNull;

/**
 * Allows specific languages to override backspace unindent mode set in global editor preferences.
 * 
 * @see SmartBackspaceDisabler
 */
public abstract class BackspaceModeOverride {
  public abstract @NotNull SmartBackspaceMode getBackspaceMode(@NotNull SmartBackspaceMode modeFromSettings);
}
