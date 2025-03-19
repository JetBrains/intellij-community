// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * See {@link DocumentationProvider} doc for replacement API.
 */
@ApiStatus.Obsolete
public class DocumentationProviderEx implements DocumentationProvider {

  /**
   * @deprecated Use/override {@link #getCustomDocumentationElement(Editor, PsiFile, PsiElement, int)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public @Nullable PsiElement getCustomDocumentationElement(final @NotNull Editor editor,
                                                  final @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    return null;
  }

  public @Nullable Image getLocalImageForElement(@NotNull PsiElement element, @NotNull String imageSpec) {
    return null;
  }
}
