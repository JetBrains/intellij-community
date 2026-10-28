// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class ModifiableFontPreferences extends FontPreferences {
  public abstract void clear();
  public abstract void clearFonts();
  public abstract void setUseLigatures(boolean useLigatures);
  public abstract void addFontFamily(String family);
  public abstract void register(String family, int size);
  public void register(String family, float size) { register(family, (int)(size + 0.5)); }
  public abstract void setEffectiveFontFamilies(List<String> fontFamilies);
  public abstract void setRealFontFamilies(List<String> fontFamilies);
  public abstract void setTemplateFontSize(int size);
  public void setTemplateFontSize(float size) { setTemplateFontSize((int)(size + 0.5)); }
  public abstract void setLineSpacing(float lineSpacing);
  public abstract void resetFontSizes();
  public abstract void setFontSize(@NotNull String fontFamily, int size);
  public void setFontSize(@NotNull String fontFamily, float size) { setFontSize(fontFamily, (int)(size + 0.5)); }
  public abstract void setRegularSubFamily(String subFamily);
  public abstract void setBoldSubFamily(String subFamily);
  public void setCharacterVariants(@Unmodifiable @NotNull Set<@NotNull String> variants) {}
  public void setCharacterVariant(@NotNull String variant, boolean enabled) {}

  /**
   * Applies the changes to these preferences.
   * An implementation can apply them as one change and can run them more than once.
   * So the changes must have no side effects.
   * Inside the changes, use only the argument, and do not keep it after the call.
   */
  @ApiStatus.Experimental
  public void runBatchUpdate(@NotNull Consumer<? super ModifiableFontPreferences> changes) {
    changes.accept(this);
  }
}
