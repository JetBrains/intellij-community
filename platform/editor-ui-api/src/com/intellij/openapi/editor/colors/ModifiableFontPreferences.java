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

public interface ModifiableFontPreferences extends FontPreferences {
  void clear();
  void clearFonts();
  void setUseLigatures(boolean useLigatures);
  void addFontFamily(String family);
  void register(String family, int size);
  void setEffectiveFontFamilies(List<String> fontFamilies);
  void setRealFontFamilies(List<String> fontFamilies);
  void setTemplateFontSize(int size);
  void setLineSpacing(float lineSpacing);
  void resetFontSizes();
  void setFontSize(@NotNull String fontFamily, int size);
}
