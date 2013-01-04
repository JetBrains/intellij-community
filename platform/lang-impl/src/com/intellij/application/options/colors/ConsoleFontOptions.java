/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.FontPreferences;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class ConsoleFontOptions extends FontOptions {
  public ConsoleFontOptions(ColorAndFontOptions options) {
    super(options, "Console Font");
  }

  @NotNull
  @Override
  protected FontPreferences getFontPreferences() {
    return getCurrentScheme().getConsoleFontPreferences();
  }

  @Override
  protected float getLineSpacing() {
    return getCurrentScheme().getConsoleLineSpacing();
  }

  @Override
  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setConsoleLineSpacing(lineSpacing);
  }
}
