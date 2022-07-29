// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.preview;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Internal utils to support intention preview feature
 */
@ApiStatus.Internal
public class IntentionPreviewUtils {
  private static final Key<Boolean> PREVIEW_MARKER = Key.create("PREVIEW_MARKER");
  private static final ThreadLocal<Editor> PREVIEW_EDITOR = new ThreadLocal<>();

  /**
   * @param file file to get the preview copy
   * @return a preview copy of the file
   */
  public static @NotNull PsiFile obtainCopyForPreview(@NotNull PsiFile file) {
    PsiFile copy = (PsiFile)file.copy();
    copy.putUserData(PREVIEW_MARKER, true);
    return copy;
  }

  /**
   * @param element
   * @return true if given element is a copy created for preview
   */
  public static boolean isPreviewElement(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && Boolean.TRUE.equals(file.getUserData(PREVIEW_MARKER));
  }

  /**
   * Start preview session with given editor (generatePreview call should be wrapped)
   * @param editor preview editor to use
   * @param runnable action to execute
   */
  public static void previewSession(@NotNull Editor editor, @NotNull Runnable runnable) {
    PREVIEW_EDITOR.set(editor);
    try {
      runnable.run();
    }
    finally {
      PREVIEW_EDITOR.remove();
    }
  }

  /**
   * @return current imaginary editor used for preview; null if we are not in preview session
   */
  public static @Nullable Editor getPreviewEditor() {
    return PREVIEW_EDITOR.get();
  }
}
