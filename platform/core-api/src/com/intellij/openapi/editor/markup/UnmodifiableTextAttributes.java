// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.DataInput;
import java.util.Map;

/**
 * Unmodifiable TextAttributes with all setters throwing exception.
 */
public class UnmodifiableTextAttributes extends TextAttributes {
  @Override
  public void copyFrom(@NotNull TextAttributes other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttributes(Color foregroundColor,
                            Color backgroundColor,
                            Color effectColor,
                            Color errorStripeColor, EffectType effectType, int fontType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setForegroundColor(Color color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBackgroundColor(Color color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setEffectColor(Color color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setErrorStripeColor(Color color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> effectsMap) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setEffectType(EffectType effectType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFontType(int type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void readExternal(@NotNull Element element) {
    throw new UnsupportedOperationException();
  }

  @ApiStatus.Internal
  @Override
  public void readExternal(@NotNull DataInput in) {
    throw new UnsupportedOperationException();
  }
}
