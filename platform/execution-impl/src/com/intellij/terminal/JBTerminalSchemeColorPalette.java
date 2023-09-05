// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.process.ColoredOutputTypeRegistryImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.jediterm.core.Color;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AwtTransformers;
import org.jetbrains.annotations.NotNull;

public final class JBTerminalSchemeColorPalette extends ColorPalette {

  private static final Logger LOG = Logger.getInstance(JBTerminalSchemeColorPalette.class);

  private final EditorColorsScheme myColorsScheme;

  JBTerminalSchemeColorPalette(@NotNull EditorColorsScheme scheme) {
    myColorsScheme = scheme;
  }

  @Override
  protected @NotNull Color getForegroundByColorIndex(int colorIndex) {
    TextAttributes attributes = myColorsScheme.getAttributes(ColoredOutputTypeRegistryImpl.getAnsiColorKey(colorIndex));
    java.awt.Color foregroundColor = attributes.getForegroundColor();
    if (foregroundColor != null) {
      return AwtTransformers.fromAwtColor(foregroundColor);
    }
    java.awt.Color backgroundColor = attributes.getBackgroundColor();
    if (backgroundColor != null) {
      return AwtTransformers.fromAwtColor(backgroundColor);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Default foreground color will be used for ANSI color index #" + colorIndex);
    }
    return AwtTransformers.fromAwtColor(myColorsScheme.getDefaultForeground());
  }

  @Override
  protected @NotNull Color getBackgroundByColorIndex(int colorIndex) {
    TextAttributes attributes = myColorsScheme.getAttributes(ColoredOutputTypeRegistryImpl.getAnsiColorKey(colorIndex));
    java.awt.Color backgroundColor = attributes.getBackgroundColor();
    if (backgroundColor != null) {
      return AwtTransformers.fromAwtColor(backgroundColor);
    }
    java.awt.Color foregroundColor = attributes.getForegroundColor();
    if (foregroundColor != null) {
      return AwtTransformers.fromAwtColor(foregroundColor);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Default background color will be used for ANSI color index #" + colorIndex);
    }
    return AwtTransformers.fromAwtColor(myColorsScheme.getDefaultBackground());
  }
}
