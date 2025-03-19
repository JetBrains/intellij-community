// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptorWithPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RainbowAttributeDescriptor implements EditorSchemeAttributeDescriptorWithPath {
  private final String myGroup;
  private final String myDisplayName;
  private final EditorColorsScheme myScheme;
  private final Language myLanguage;
  private final RainbowColorsInSchemeState myRainbowColorsInSchemaState;

  RainbowAttributeDescriptor(@Nullable Language language,
                                    @NotNull String group,
                                    @NotNull String displayNameWithPath,
                                    @NotNull EditorColorsScheme scheme,
                                    @NotNull RainbowColorsInSchemeState rainbowState) {
    myLanguage = language;
    myDisplayName = displayNameWithPath;
    myRainbowColorsInSchemaState = rainbowState;
    myScheme = scheme;
    myGroup = group;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }

  @Override
  public String getGroup() {
    return myGroup;
  }

  @Override
  public String getType() {
    return RainbowHighlighter.RAINBOW_TYPE;
  }

  @Override
  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  @Override
  public void apply(@Nullable EditorColorsScheme scheme) {
    myRainbowColorsInSchemaState.apply(scheme);
  }

  @Override
  public boolean isModified() {
    return myRainbowColorsInSchemaState.isModified(myLanguage);
  }

  public Language getLanguage() {
    return myLanguage;
  }
}
