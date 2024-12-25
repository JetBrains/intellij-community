// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntUnaryOperator;

/**
 * Represents sub text of other content.
 */
@ApiStatus.Internal
public final class DocumentFragmentContent extends DiffContentBase implements DocumentContent {
  private final @NotNull DocumentContent myOriginal;
  private final @NotNull RangeMarker myRangeMarker;

  private final @NotNull MyDocumentsSynchronizer mySynchronizer;

  private int myAssignments = 0;

  public DocumentFragmentContent(@Nullable Project project, @NotNull DocumentContent original, @NotNull TextRange range) {
    this(project, original, createRangeMarker(original.getDocument(), range));
  }

  public DocumentFragmentContent(@Nullable Project project, @NotNull DocumentContent original, @NotNull RangeMarker rangeMarker) {
    myOriginal = original;
    myRangeMarker = rangeMarker;

    Document document1 = myOriginal.getDocument();
    Document document2 = DocumentsSynchronizer.createFakeDocument(document1);

    mySynchronizer = new MyDocumentsSynchronizer(project, myRangeMarker, document1, document2);

    IntUnaryOperator originalLineConvertor = original.getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR);
    putUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR, value -> {
      if (!myRangeMarker.isValid()) return -1;
      int line = value + document1.getLineNumber(myRangeMarker.getStartOffset());
      return originalLineConvertor != null ? originalLineConvertor.applyAsInt(line) : line;
    });
  }

  private static @NotNull RangeMarker createRangeMarker(@NotNull Document document, @NotNull TextRange range) {
    RangeMarker rangeMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    return rangeMarker;
  }

  @Override
  public @NotNull Document getDocument() {
    return mySynchronizer.getDocument2();
  }

  @Override
  public @Nullable VirtualFile getHighlightFile() {
    return myOriginal.getHighlightFile();
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull LineCol position) {
    if (!myRangeMarker.isValid()) return null;
    int offset = position.toOffset(getDocument());
    int originalOffset = offset + myRangeMarker.getStartOffset();
    LineCol originalPosition = LineCol.fromOffset(myOriginal.getDocument(), originalOffset);
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

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) {
      if (myAssignments == 0) mySynchronizer.startListen();
      myAssignments++;
    }
    else {
      myAssignments--;
      if (myAssignments == 0) mySynchronizer.stopListen();
    }
    assert myAssignments >= 0;
  }

  private static class MyDocumentsSynchronizer extends DocumentsSynchronizer {
    private final @NotNull RangeMarker myRangeMarker;

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
        try {
          replaceString(myDocument2, 0, myDocument2.getTextLength(),
                        DiffBundle.message("synchronize.document.and.its.fragment.range.error"));
        }
        finally {
          myDocument2.setReadOnly(true);
        }
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
      // no need to set myDuringModification - listeners are not added yet
      CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (myRangeMarker.isValid()) {
            myDocument2.setReadOnly(false);
            CharSequence nexText = myRangeMarker.getTextRange().subSequence(myDocument1.getCharsSequence());
            myDocument2.setText(nexText);
            myDocument2.setReadOnly(!myDocument1.isWritable());
          }
          else {
            myDocument2.setReadOnly(false);
            try {
              myDocument2.setText(DiffBundle.message("synchronize.document.and.its.fragment.range.error"));
            } finally {
              myDocument2.setReadOnly(true);
            }
          }
        });
      });

      super.startListen();
    }

    @RequiresEdt
    private void replaceString(@NotNull Document document,
                               int startOffset,
                               int endOffset,
                               @NotNull CharSequence newText) {
      try {
        myDuringModification = true;
        document.replaceString(startOffset, endOffset, newText);
      }
      finally {
        myDuringModification = false;
      }
    }
  }
}
