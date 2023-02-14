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
}
