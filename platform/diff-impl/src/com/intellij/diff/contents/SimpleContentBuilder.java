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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class SimpleContentBuilder {
  @NotNull private final String myText;

  @Nullable private FileType myFileType;
  @Nullable private VirtualFile myHighlightFile;
  @Nullable private LineSeparator mySeparator;
  @Nullable private Charset myCharset;
  @Nullable private Convertor<Integer, OpenFileDescriptor> myOpenFileDescriptor;
  private boolean myReadOnly;

  public SimpleContentBuilder(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public SimpleContentBuilder setFileType(@Nullable FileType type) {
    myFileType = type;
    return this;
  }

  @NotNull
  public SimpleContentBuilder setHighlightFile(@Nullable VirtualFile highlightFile) {
    myHighlightFile = highlightFile;
    return this;
  }

  @NotNull
  public SimpleContentBuilder setSeparator(@Nullable LineSeparator separator) {
    mySeparator = separator;
    return this;
  }

  @NotNull
  public SimpleContentBuilder setCharset(@Nullable Charset charset) {
    myCharset = charset;
    return this;
  }

  @NotNull
  public SimpleContentBuilder setOpenFileDescriptor(@Nullable Convertor<Integer, OpenFileDescriptor> openFileDescriptor) {
    myOpenFileDescriptor = openFileDescriptor;
    return this;
  }

  @NotNull
  public SimpleContentBuilder setReadOnly(boolean readOnly) {
    myReadOnly = readOnly;
    return this;
  }

  @NotNull
  public DocumentContent build() {
    Document document = EditorFactory.getInstance().createDocument(myText);
    if (myReadOnly) document.setReadOnly(true);
    return new DocumentContentImpl(document, myFileType, myHighlightFile, mySeparator, myCharset) {
      @Nullable
      @Override
      public OpenFileDescriptor getOpenFileDescriptor(int offset) {
        if (myOpenFileDescriptor == null) return null;
        return myOpenFileDescriptor.convert(offset);
      }
    };
  }
}
