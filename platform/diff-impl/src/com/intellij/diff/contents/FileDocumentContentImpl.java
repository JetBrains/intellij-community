// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.contents;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class FileDocumentContentImpl extends DocumentContentBase implements FileContent {
  private final @NotNull VirtualFile myFile;
  private final @Nullable VirtualFile myHighlightFile;

  public FileDocumentContentImpl(@Nullable Project project,
                                 @NotNull Document document,
                                 @NotNull VirtualFile file) {
    this(project, document, file, null);
  }

  public FileDocumentContentImpl(@Nullable Project project,
                                 @NotNull Document document,
                                 @NotNull VirtualFile file,
                                 @Nullable VirtualFile highlightFile) {
    super(project, document);
    myFile = file;
    myHighlightFile = highlightFile;
  }

  @Override
  public @Nullable VirtualFile getHighlightFile() {
    return ObjectUtils.chooseNotNull(myHighlightFile, myFile);
  }

  @Override
  public @Nullable LineSeparator getLineSeparator() {
    String s = LoadTextUtil.detectLineSeparator(myFile, true);
    if (s == null) return null;
    return LineSeparator.fromString(s);
  }

  @Override
  public @Nullable Charset getCharset() {
    return myFile.getCharset();
  }

  @Override
  public @Nullable Boolean hasBom() {
    return myFile.getBOM() != null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public @Nullable FileType getContentType() {
    return myFile.getFileType();
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) DiffUtil.refreshOnFrameActivation(myFile);
  }
}
