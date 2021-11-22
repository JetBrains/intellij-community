// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntUnaryOperator;

/**
 * Represents snapshot of sub text of other content.
 */
public final class ImmutableDocumentFragmentContent extends DiffContentBase implements DocumentContent {
  @NotNull private final DocumentContent myOriginal;
  @NotNull private final Document myDocument;

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

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myOriginal.getHighlightFile();
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull LineCol position) {
    LineCol originalPosition = new LineCol(myStartLine + position.line, position.column);
    return myOriginal.getNavigatable(originalPosition);
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myOriginal.getContentType();
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return getNavigatable(new LineCol(0));
  }
}
