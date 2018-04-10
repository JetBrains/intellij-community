/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public class DelegatingFontPreferences extends FontPreferences {
  private final Supplier<FontPreferences> myDelegateSupplier;

  public DelegatingFontPreferences(@NotNull Supplier<FontPreferences> delegateSupplier) {
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
