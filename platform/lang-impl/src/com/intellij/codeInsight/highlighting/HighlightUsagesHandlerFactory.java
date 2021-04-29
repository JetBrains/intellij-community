// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see HighlightUsagesHandlerFactoryBase
 */
public interface HighlightUsagesHandlerFactory {
  ExtensionPointName<HighlightUsagesHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.highlightUsagesHandlerFactory");

  @Nullable
  HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file);


  /**
   * @param visibleRange To avoid parsing in EDT, these factory methods should be called in a background thread
   *                      (as implementation use the PSI element under cursor to choose the specific handler).
   *                      However, some handlers require the editor visible range, which must be calculated in EDT,
   *                      so it's passed externally
   */
  @Nullable
  default HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull ProperTextRange visibleRange) {
    return createHighlightUsagesHandler(editor, file);
  }
}
