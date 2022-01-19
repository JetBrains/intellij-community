// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public final class DelegatingFontPreferences extends FontPreferences {
  private final Supplier<? extends FontPreferences> myDelegateSupplier;

  public DelegatingFontPreferences(@NotNull Supplier<? extends FontPreferences> delegateSupplier) {
    myDelegateSupplier = delegateSupplier;
  }

  @NotNull
  @Override
  public List<String> getEffectiveFontFamilies() {
    return myDelegateSupplier.get().getEffectiveFontFamilies();
  }

  @NotNull
  @Override
  public List<String> getRealFontFamilies() {
    return myDelegateSupplier.get().getRealFontFamilies();
  }

  @NotNull
  @Override
  public String getFontFamily() {
    return myDelegateSupplier.get().getFontFamily();
  }

  @Override
  public @Nullable String getRegularSubFamily() {
    return myDelegateSupplier.get().getRegularSubFamily();
  }

  @Override
  public @Nullable String getBoldSubFamily() {
    return myDelegateSupplier.get().getBoldSubFamily();
  }

  @Override
  public int getSize(@NotNull String fontFamily) {
    return myDelegateSupplier.get().getSize(fontFamily);
  }

  @Override
  public void copyTo(@NotNull FontPreferences preferences) {
    myDelegateSupplier.get().copyTo(preferences);
  }

  @Override
  public boolean useLigatures() {
    return myDelegateSupplier.get().useLigatures();
  }

  @Override
  public boolean hasSize(@NotNull String fontName) {
    return myDelegateSupplier.get().hasSize(fontName);
  }

  @Override
  public float getLineSpacing() {
    return myDelegateSupplier.get().getLineSpacing();
  }


}
