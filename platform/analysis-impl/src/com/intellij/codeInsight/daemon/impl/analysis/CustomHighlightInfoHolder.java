/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey
 */
public class CustomHighlightInfoHolder extends HighlightInfoHolder {
  private final EditorColorsScheme myCustomColorsScheme;

  public CustomHighlightInfoHolder(@NotNull final PsiFile contextFile,
                                   @NotNull final HighlightInfoFilter... filters) {
    this(contextFile, null, filters);
  }

  public CustomHighlightInfoHolder(@NotNull final PsiFile contextFile,
                                   @Nullable final EditorColorsScheme customColorsScheme,
                                   @NotNull final HighlightInfoFilter... filters) {
    super(contextFile, filters);
    myCustomColorsScheme = customColorsScheme;
  }

  @Override
  @NotNull
  public TextAttributesScheme getColorsScheme() {
    if (myCustomColorsScheme != null) {
      return myCustomColorsScheme;
    }
    return EditorColorsManager.getInstance().getGlobalScheme();
  }
}
