// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nullable FileType myType;
  private final @Nullable VirtualFile myHighlightFile;

  private final @Nullable LineSeparator mySeparator;
  private final @Nullable Charset myCharset;
  private final @Nullable Boolean myBOM;

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

  @Override
  public @Nullable VirtualFile getHighlightFile() {
    return myHighlightFile;
  }

  @Override
  public @Nullable LineSeparator getLineSeparator() {
    return mySeparator;
  }

  @Override
  public @Nullable Boolean hasBom() {
    return myBOM;
  }

  @Override
  public @Nullable FileType getContentType() {
    return myType;
  }

  @Override
  public @Nullable Charset getCharset() {
    return myCharset;
  }
}
