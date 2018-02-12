/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(EditorCopyPasteHelper.class);
  }

  /**
   * Copies text selected in editor to clipboard.
   */
  public abstract void copySelectionToClipboard(@NotNull Editor editor);

  /**
   * Pastes from clipboard into editor at caret(s) position.
   *
   * @return ranges of text in the document, corresponding to pasted fragments, if paste succeeds, or {@code null} otherwise
   */
  @Nullable
  public abstract TextRange[] pasteFromClipboard(@NotNull Editor editor);

  /**
   * Pastes given Transferable instance into editor at caret(s) position.
   *
   * @return ranges of text in the document, corresponding to pasted fragments, if paste succeeds, or {@code null} otherwise
   */
  @Nullable
  public abstract TextRange[] pasteTransferable(@NotNull Editor editor, @NotNull Transferable content);
}
