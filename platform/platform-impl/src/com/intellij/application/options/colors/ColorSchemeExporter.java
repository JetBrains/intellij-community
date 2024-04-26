// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.application.options.schemes.SerializableSchemeExporter;
import com.intellij.openapi.editor.colors.EditorColorsManager;

/**
 * Exports a scheme as .icls file.
 */
public final class ColorSchemeExporter extends SerializableSchemeExporter {

  @Override
  public String getExtension() {
    return EditorColorsManager.COLOR_SCHEME_FILE_EXTENSION.substring(1);
  }
}
