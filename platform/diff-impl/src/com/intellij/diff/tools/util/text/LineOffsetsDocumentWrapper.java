// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

final class LineOffsetsDocumentWrapper implements LineOffsets {
  @NotNull private final Document myDocument;

  LineOffsetsDocumentWrapper(@NotNull Document document) {
    myDocument = document;
  }

  @Override
  public int getLineStart(int line) {
    return myDocument.getLineStartOffset(line);
  }

  @Override
  public int getLineEnd(int line) {
    return myDocument.getLineEndOffset(line);
  }

  @Override
  public int getLineEnd(int line, boolean includeNewline) {
    if (myDocument.getLineCount() == 0) return 0;
    return myDocument.getLineEndOffset(line) + (includeNewline ? myDocument.getLineSeparatorLength(line) : 0);
  }

  @Override
  public int getLineNumber(int offset) {
    return myDocument.getLineNumber(offset);
  }

  @Override
  public int getLineCount() {
    return DiffUtil.getLineCount(myDocument);
  }

  @Override
  public int getTextLength() {
    return myDocument.getTextLength();
  }
}
