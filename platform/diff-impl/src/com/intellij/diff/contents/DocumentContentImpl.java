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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Allows to compare some text associated with document.
 */
public class DocumentContentImpl extends DocumentContentBase {
  @Nullable private final FileType myType;
  @Nullable private final VirtualFile myHighlightFile;

  @Nullable private final LineSeparator mySeparator;
  @Nullable private final Charset myCharset;
  @Nullable private final Boolean myBOM;

  public DocumentContentImpl(@NotNull Document document) {
    this(null, document, null);
  }

  public DocumentContentImpl(@Nullable Project project,
                             @NotNull Document document,
                             @Nullable FileType type) {
    this(project, document, type, null, null, null, null);
  }

  public DocumentContentImpl(@Nullable Project project,
                             @NotNull Document document,
                             @Nullable FileType type,
                             @Nullable VirtualFile highlightFile,
                             @Nullable LineSeparator separator,
                             @Nullable Charset charset,
                             @Nullable Boolean bom) {
    super(project, document);
    myType = type;
    myHighlightFile = highlightFile;
    mySeparator = separator;
    myCharset = charset;
    myBOM = bom;
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myHighlightFile;
  }

  @Nullable
  @Override
  public LineSeparator getLineSeparator() {
    return mySeparator;
  }

  @Override
  @Nullable
  public Boolean hasBom() {
    return myBOM;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myType;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myCharset;
  }
}
