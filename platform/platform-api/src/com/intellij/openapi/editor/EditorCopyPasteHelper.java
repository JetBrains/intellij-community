// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

/**
 * Support for data transfer between editor and clipboard.
 */
public abstract class EditorCopyPasteHelper {
  /**
   *  Setup JTextComponent.getDocument().putProperty(TRIM_TEXT_ON_PASTE_KEY, Boolean.TRUE) to trim text that is being pasted
   */
  public static final String TRIM_TEXT_ON_PASTE_KEY = "trimTextOnPaste";

  public static EditorCopyPasteHelper getInstance() {
    return ApplicationManager.getApplication().getService(EditorCopyPasteHelper.class);
  }

  /**
   * Copies text selected in editor to clipboard.
   */
  public abstract void copySelectionToClipboard(@NotNull Editor editor);

  /**
   * Pastes from clipboard into editor at caret(s) position.
   *
   * @return ranges of text in the document, corresponding to pasted fragments, if paste succeeds, or {@code null} otherwise
   *
   * @throws TooLargeContentException if content is too large to be pasted in editor
   */
  public abstract TextRange @Nullable [] pasteFromClipboard(@NotNull Editor editor) throws TooLargeContentException;

  /**
   * Pastes given Transferable instance into editor at caret(s) position.
   *
   * @return ranges of text in the document, corresponding to pasted fragments, if paste succeeds, or {@code null} otherwise
   *
   * @throws TooLargeContentException if content is too large to be pasted in editor
   */
  public abstract TextRange @Nullable [] pasteTransferable(@NotNull Editor editor, @NotNull Transferable content) throws TooLargeContentException;

  public static class TooLargeContentException extends RuntimeException {
    private final int contentLength;

    public TooLargeContentException(int contentLength) {
      this.contentLength = contentLength;
    }

    public int getContentLength() {
      return contentLength;
    }
  }
}
