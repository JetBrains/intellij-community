// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.preview;

import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Possible result for IntentionPreview
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

    public @NotNull HtmlChunk content() {
      return myContent;
    }
  }
}
