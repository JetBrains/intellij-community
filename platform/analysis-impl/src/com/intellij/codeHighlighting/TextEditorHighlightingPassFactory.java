// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeHighlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Register via {@link TextEditorHighlightingPassFactoryRegistrar}.
 * <p>
 * Implement {@link com.intellij.openapi.project.DumbAware} to allow creating {@link HighlightingPass} during index updates.
 */
public interface TextEditorHighlightingPassFactory extends PossiblyDumbAware {
  /**
   * Create the {@link HighlightingPass} this factory is responsible for.
   * Can return {@code null} if the {@link HighlightingPass} should not be run this time (for example, the factory doesn't like the file passed to it).
   * There's no guarantees about the thread this method is called in, so the necessary precautions should be taken in case of background thread.
   * For example, read action should be acquired before accessing PSI.
   */
  @Nullable
  TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor);
}
