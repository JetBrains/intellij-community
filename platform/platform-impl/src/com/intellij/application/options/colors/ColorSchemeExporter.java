// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.application.options.schemes.SerializableSchemeExporter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * Exports a scheme as .icls file.
 */
@ApiStatus.Internal
public final class ColorSchemeExporter extends SerializableSchemeExporter {

  @Override
  public String getExtension() {
    return EditorColorsManager.getColorSchemeFileExtension().substring(1);
  }
}
