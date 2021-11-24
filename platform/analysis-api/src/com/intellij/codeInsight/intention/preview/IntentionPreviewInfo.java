// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.preview;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Possible result for IntentionPreview.
 * @see com.intellij.codeInsight.intention.IntentionAction#generatePreview(Project, Editor, PsiFile)
 * @see com.intellij.codeInspection.LocalQuickFix#generatePreview(Project, ProblemDescriptor)
 */
@ApiStatus.NonExtendable
public interface IntentionPreviewInfo {
  /**
   * No intention preview is available.
   */
  IntentionPreviewInfo EMPTY = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  /**
   * Try to use fallback mechanism for intention preview instead.
   * Do not use this directly
   */
  @ApiStatus.Internal
  IntentionPreviewInfo FALLBACK_DIFF = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "FALLBACK";
    }
  };

  /**
   * Changes in the file copy should be displayed as intention preview
   */
  IntentionPreviewInfo DIFF = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "DIFF";
    }
  };

  /**
   * HTML description
   */
  class Html implements IntentionPreviewInfo {
    private final @NotNull HtmlChunk myContent;

    public Html(@NotNull HtmlChunk content) {
      myContent = content;
    }

    public Html(@Nls @NotNull String contentHtml) {
      this(HtmlChunk.raw(contentHtml));
    }

    /**
     * @return HTML content
     */
    public @NotNull HtmlChunk content() {
      return myContent;
    }
  }
}
