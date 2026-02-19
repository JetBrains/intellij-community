// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.FontPreferences;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ColorAndFontSettingsListener extends EventListener {
  void selectedOptionChanged(@NotNull Object selected);
  void schemeChanged(@NotNull Object source);
  default void schemeReset(@NotNull FontPreferences fontPreferences) {}
  void settingsChanged();
  void selectionInPreviewChanged(@NotNull String typeToSelect);

  void fontChanged();

  abstract class Abstract implements ColorAndFontSettingsListener {
    @Override
    public void selectedOptionChanged(final @NotNull Object selected) {

    }

    @Override
    public void schemeChanged(final @NotNull Object source) {
    }

    @Override
    public void settingsChanged() {
    }

    @Override
    public void selectionInPreviewChanged(final @NotNull String typeToSelect) {
    }

    @Override
    public void fontChanged() {
      
    }
  }
}
