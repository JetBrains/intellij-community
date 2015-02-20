/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.contents;

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
