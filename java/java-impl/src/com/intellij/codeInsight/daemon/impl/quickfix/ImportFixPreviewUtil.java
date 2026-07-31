// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ImportFixPreviewUtil {
  private ImportFixPreviewUtil() { }

  /**
   * Locates {@code element} inside the preview copy {@code previewFile}.
   * <p>
   * Import fixes keep the reference they were created for, but the file passed to
   * {@link com.intellij.codeInsight.intention.IntentionAction#generatePreview} is not necessarily a copy of the file
   * that reference belongs to: it may be an injected fragment under the caret, the injection host, or another root of a
   * multi-root view provider (JSP). Passing such a file to {@link PsiTreeUtil#findSameElementInCopy} makes it walk out
   * of the file and throw {@link IllegalStateException}, so the mismatch is detected here instead.
   *
   * @return the corresponding element in the copy, or null if {@code previewFile} is not a preview copy of the file
   * containing {@code element}
   */
  static <T extends PsiElement> @Nullable T findSameElementInPreview(@NotNull PsiFile previewFile, @Nullable T element) {
    if (element == null || !element.isValid()) return null;
    if (IntentionPreviewUtils.getOriginalFile(previewFile) != element.getContainingFile()) return null;
    return PsiTreeUtil.findSameElementInCopy(element, previewFile);
  }
}
