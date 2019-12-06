// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author peter
 */
public class DocumentationProviderEx implements DocumentationProvider {

  /**
   * @deprecated Use/override {@link #getCustomDocumentationElement(Editor, PsiFile, PsiElement, int)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Nullable
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    return null;
  }

  @Nullable
  public Image getLocalImageForElement(@NotNull PsiElement element, @NotNull String imageSpec) {
    return null;
  }
}
