// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntUnaryOperator;

/**
 * Represents sub text of other content.
 */
public final class DocumentFragmentContent extends SynchronizedDocumentContent {
  // TODO: reuse DocumentWindow ?
  @NotNull private final RangeMarker myRangeMarker;

  @NotNull private final MyDocumentsSynchronizer mySynchronizer;

  public DocumentFragmentContent(@Nullable Project project, @NotNull DocumentContent original, @NotNull TextRange range) {
    this(project, original, createRangeMarker(original.getDocument(), range));
  }

  public DocumentFragmentContent(@Nullable Project project, @NotNull DocumentContent original, @NotNull RangeMarker rangeMarker) {
    super(original);
    myRangeMarker = rangeMarker;

    Document document1 = getOriginal().getDocument();

    mySynchronizer = new MyDocumentsSynchronizer(project, myRangeMarker, document1, getFakeDocument());

    IntUnaryOperator originalLineConvertor = original.getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR);
    putUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR, value -> {
      if (!myRangeMarker.isValid()) return -1;
      int line = value + document1.getLineNumber(myRangeMarker.getStartOffset());
      return originalLineConvertor != null ? originalLineConvertor.applyAsInt(line) : line;
    });
  }

  @NotNull
  private static RangeMarker createRangeMarker(@NotNull Document document, @NotNull TextRange range) {
    RangeMarker rangeMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    return rangeMarker;
  }

  @NotNull
  @Override
  public Document getDocument() {
    return mySynchronizer.getDocument2();
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return getOriginal().getHighlightFile();
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull LineCol position) {
    if (!myRangeMarker.isValid()) return null;
    int offset = position.toOffset(getDocument());
    int originalOffset = offset + myRangeMarker.getStartOffset();
    LineCol originalPosition = LineCol.fromOffset(getOriginal().getDocument(), originalOffset);
    return getOriginal().getNavigatable(originalPosition);
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return getOriginal().getContentType();
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return getNavigatable(new LineCol(0));
  }

  @NotNull
  @Override
  public DocumentsSynchronizer getSynchronizer() {
    return mySynchronizer;
  }

  private static class MyDocumentsSynchronizer extends DocumentsSynchronizer {
    @NotNull private final RangeMarker myRangeMarker;

    MyDocumentsSynchronizer(@Nullable Project project,
                                   @NotNull RangeMarker range,
                                   @NotNull Document document1,
                                   @NotNull Document document2) {
      super(project, document1, document2);
      myRangeMarker = range;
    }

    @Override
    protected void onDocumentChanged1(@NotNull DocumentEvent event) {
      if (!myRangeMarker.isValid()) {
        myDocument2.setReadOnly(false);
        replaceString(myDocument2, 0, myDocument2.getTextLength(), DiffBundle.message("synchronize.document.and.its.fragment.range.error"));
        myDocument2.setReadOnly(true);
        return;
      }
      CharSequence newText = myDocument1.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
      replaceString(myDocument2, 0, myDocument2.getTextLength(), newText);
    }

    @Override
    protected void onDocumentChanged2(@NotNull DocumentEvent event) {
      if (!myRangeMarker.isValid()) return;
      if (!myDocument1.isWritable()) return;

      CharSequence newText = event.getNewFragment();
      int originalOffset = event.getOffset() + myRangeMarker.getStartOffset();
      int originalEnd = originalOffset + event.getOldLength();
      replaceString(myDocument1, originalOffset, originalEnd, newText);
    }

    @Override
    public void startListen() {
      if (myRangeMarker.isValid()) {
        myDocument2.setReadOnly(false);
        CharSequence nexText = myDocument1.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
        replaceString(myDocument2, 0, myDocument2.getTextLength(), nexText);
        myDocument2.setReadOnly(!myDocument1.isWritable());
      }
      else {
        myDocument2.setReadOnly(false);
        replaceString(myDocument2, 0, myDocument2.getTextLength(), DiffBundle.message("synchronize.document.and.its.fragment.range.error"));
        myDocument2.setReadOnly(true);
      }
      super.startListen();
    }
  }
}
