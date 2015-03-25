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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

@Deprecated
/** @deprecated (to be removed in IDEA 15) */
public class DocumentContentWrapper implements DocumentContent {
  @NotNull private final DocumentContent myBase;
  @NotNull private final Document myDocument;

  public DocumentContentWrapper(@NotNull Document document, @NotNull DocumentContent base) {
    myDocument = document;
    myBase = base;
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myBase.getHighlightFile();
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return myBase.getOpenFileDescriptor(offset);
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return null;
  }

  @Nullable
  @Override
  public LineSeparator getLineSeparator() {
    return null;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myBase.getContentType();
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor() {
    return getOpenFileDescriptor(0);
  }

  @Override
  public void onAssigned(boolean isAssigned) {
  }
}
