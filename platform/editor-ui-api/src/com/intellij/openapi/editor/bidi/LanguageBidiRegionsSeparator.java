/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.bidi;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @see BidiRegionsSeparator
 */
public class LanguageBidiRegionsSeparator extends LanguageExtension<BidiRegionsSeparator> {
  public static final LanguageBidiRegionsSeparator INSTANCE = new LanguageBidiRegionsSeparator();
  
  private LanguageBidiRegionsSeparator() {
    super("com.intellij.bidiRegionsSeparator", new BidiRegionsSeparator() {
      @Override
      public boolean createBorderBetweenTokens(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
        return true;
      }
    });
  }
}
