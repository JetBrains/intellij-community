// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeHighlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Register via {@link TextEditorHighlightingPassFactoryRegistrar}.
 */
public interface TextEditorHighlightingPassFactory {
  @Nullable
  TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor);
}
