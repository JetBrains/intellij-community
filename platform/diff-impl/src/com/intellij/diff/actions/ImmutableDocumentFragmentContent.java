// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntUnaryOperator;

/**
 * Represents snapshot of sub text of other content.
 */
@ApiStatus.Internal
public final class ImmutableDocumentFragmentContent extends DiffContentBase implements DocumentContent {
  private final @NotNull DocumentContent myOriginal;
  private final @NotNull Document myDocument;

  private final int myStartLine;

  public ImmutableDocumentFragmentContent(@NotNull DocumentContent original, @NotNull TextRange range) {
    myOriginal = original;

    Document originalDocument = myOriginal.getDocument();
    myStartLine = originalDocument.getLineNumber(range.getStartOffset());

    String text = StringUtil.convertLineSeparators(range.subSequence(originalDocument.getImmutableCharSequence()).toString());
    myDocument = EditorFactory.getInstance().createDocument(text);
    myDocument.setReadOnly(true);

    IntUnaryOperator originalLineConvertor = original.getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR);
    putUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR, value -> {
      int line = myStartLine + value;
      return originalLineConvertor != null ? originalLineConvertor.applyAsInt(line) : line;
    });
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @Nullable VirtualFile getHighlightFile() {
    return myOriginal.getHighlightFile();
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull LineCol position) {
    LineCol originalPosition = new LineCol(myStartLine + position.line, position.column);
    return myOriginal.getNavigatable(originalPosition);
  }

  @Override
  public @Nullable FileType getContentType() {
    return myOriginal.getContentType();
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    return getNavigatable(new LineCol(0));
  }
}
