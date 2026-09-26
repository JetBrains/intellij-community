// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.breadcrumbs;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.ApiStatus.Internal;

/**
 * Represents information about sticky line collected by {@link com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector}.
 * <p>
 * See also {@link com.intellij.openapi.editor.impl.stickyLines.StickyLine}
 */
@Internal
public record StickyLineInfo(int textOffset, int endOffset, @Nullable String debugText) {

  public StickyLineInfo(@NotNull TextRange textRange) {
    this(textRange.getStartOffset(), textRange.getEndOffset(), null);
  }

  public StickyLineInfo(@NotNull PsiElement element) {
    this(
      element.getTextOffset(),
      element.getTextRange().getEndOffset(),
      debugTextPsiElement(element)
    );
  }

  public StickyLineInfo {
    if (textOffset >= endOffset) {
      // IJPL-217619
      throw new IllegalArgumentException(String.format(
        "sticky line endOffset %s should be less than startOffset %s", textOffset, endOffset
      ));
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof StickyLineInfo info)) return false;
    return endOffset == info.endOffset && textOffset == info.textOffset;
  }

  @Override
  public int hashCode() {
    return textOffset + 31 * endOffset;
  }

  private static @Nullable String debugTextPsiElement(@NotNull PsiElement element) {
    if (Registry.is("editor.show.sticky.lines.debug")) {
      return element.toString();
    } else {
      return null;
    }
  }
}
