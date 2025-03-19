// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.editor;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class Page {
  private final String text;
  private final long pageNumber;
  private final boolean isLastInFile;

  public Page(String text, long pageNumber, boolean isLastInFile) {
    this.text = text;
    this.pageNumber = pageNumber;
    this.isLastInFile = isLastInFile;
  }

  public String getText() {
    return text;
  }

  public long getPageNumber() {
    return pageNumber;
  }

  public boolean isLastInFile() {
    return isLastInFile;
  }
}
