package com.intellij.openapi.util.diff.contents;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public interface DocumentContent extends DiffContent {
  /**
   * Represents this content as Document
   */
  @NotNull
  Document getDocument();

  /**
   * This file could be used for better syntax highlighting.
   * Some file types can't be highlighted properly depending only on their FileType (ex: SQL dialects, PHP templates).
   */
  @Nullable
  VirtualFile getHighlightFile();

  /**
   * Provides a way to open given text place in editor
   */
  @Nullable
  OpenFileDescriptor getOpenFileDescriptor(int offset);

  /**
   * @return original file line separator
   */
  @Nullable
  LineSeparator getLineSeparator();

  /**
   * @return original file charset
   */
  @Nullable
  Charset getCharset();
}
