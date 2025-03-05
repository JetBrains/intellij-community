// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.contents;

import com.intellij.diff.util.LineCol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public interface DocumentContent extends DiffContent {
  /**
   * Represents this content as Document.
   * <p>
   * Typically, files are converted into {@link LineSeparator#LF} for comparison. Use {@link #getLineSeparator()} to indicate original separators.
   */
  @NotNull
  Document getDocument();

  /**
   * This file could be used for better syntax highlighting.
   * Some file types can't be highlighted properly depending only on their FileType (ex: SQL dialects, PHP templates).
   */
  default @Nullable VirtualFile getHighlightFile() { return null; }

  /**
   * Provides a way to open given text place in editor
   */
  default @Nullable Navigatable getNavigatable(@NotNull LineCol position) { return null; }

  /**
   * @return original file line separator, used to display differences in separators.
   * {@code null} means it is 'Undefined/Not applicable' and will not be compared.
   */
  default @Nullable LineSeparator getLineSeparator() { return null; }

  /**
   * @return original file charset, used to display differences in files charsets.
   * {@code null} means it is 'Undefined/Not applicable' and will not be compared.
   * @see #hasBom()
   */
  default @Nullable Charset getCharset() { return null; }

  /**
   * @return original file byte order mark, used to display differences in files charsets.
   * {@code null} means it is 'Undefined/Not applicable' and will not be compared.
   */
  default @Nullable Boolean hasBom() { return null; }

  /**
   * @deprecated isn't called by the platform anymore
   */
  @Deprecated(forRemoval = true)
  default @Nullable OpenFileDescriptor getOpenFileDescriptor(int offset) {
    LineCol position = LineCol.fromOffset(getDocument(), offset);
    return ObjectUtils.tryCast(getNavigatable(position), OpenFileDescriptor.class);
  }
}
