/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Language-dependent implementation need to be inherited from this class and
 */
public class RainbowIdentifierHighlighterPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public RainbowIdentifierHighlighterPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, false, -1);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "RainbowIdentifierPassFactory";
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    if (RainbowHighlighter.isRainbowEnabled() && isValidContext(file, editor)) {
      return getRainbowPass(file, editor);
    }

    return null;
  }

  /**
   * Need to be rewritten in language-dependent implementation.
   * Default implementation colors all identifiers in [PsiReference] and [PsiNameIdentifierOwner] elements.
   */
  @NotNull
  protected RainbowIdentifierHighlighterPass getRainbowPass(@NotNull PsiFile file, @NotNull Editor editor) {
    return new RainbowIdentifierHighlighterPass(file, editor);
  }

  /**
   * Need to be rewritten in language-dependent implementation.
   * For example:
   *    return file instanceof JavaFile;
   * Default implementation works for any language.
   */
  protected boolean isValidContext(@NotNull final PsiFile file, @NotNull Editor editor) {
    return true;
  }
}
