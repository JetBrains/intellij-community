// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.RecursionManager;
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
    return RecursionManager.doPreventingRecursion(getClass(), false,
                                                  () -> getCustomDocumentationElement(editor, file, contextElement,
                                                                                      editor.getCaretModel().getOffset()));
  }

  /**
   * Override this method if standard platform's choice for target PSI element to show documentation for (element either declared or
   * referenced at target offset) isn't suitable for your language. For example, it could be a keyword where there's no
   * {@link com.intellij.psi.PsiReference}, but for which users might benefit from context help.
   *
   * @param targetOffset equals to caret offset for 'Quick Documentation' action, and to offset under mouse cursor for documentation shown
   *                     on mouse hover
   * @param contextElement the leaf PSI element in {@code file} at target offset
   * @return target PSI element to show documentation for, or {@code null} if it should be determined by standard platform's logic (default
   * behaviour)
   */
  @Nullable
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable PsiElement contextElement,
                                                  int targetOffset) {
    return getCustomDocumentationElement(editor, file, contextElement);
  }

  @Nullable
  public Image getLocalImageForElement(@NotNull PsiElement element, @NotNull String imageSpec) {
    return null;
  }
}
