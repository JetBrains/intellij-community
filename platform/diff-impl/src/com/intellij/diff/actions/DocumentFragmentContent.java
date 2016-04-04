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
package com.intellij.diff.actions;

import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Represents sub text of other content.
 */
public class DocumentFragmentContent extends DiffContentBase implements DocumentContent {
  // TODO: reuse DocumentWindow ?

  @NotNull private final DocumentContent myOriginal;

  @NotNull private final MyDocumentsSynchronizer mySynchonizer;

  private int myAssignments = 0;

  public DocumentFragmentContent(@Nullable Project project, @NotNull DocumentContent original, @NotNull TextRange range) {
    myOriginal = original;

    Document document1 = myOriginal.getDocument();

    Document document2 = EditorFactory.getInstance().createDocument("");
    document2.putUserData(UndoManager.ORIGINAL_DOCUMENT, document1);

    RangeMarker rangeMarker = document1.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);

    mySynchonizer = new MyDocumentsSynchronizer(project, rangeMarker, document1, document2);
  }

  @NotNull
  @Override
  public Document getDocument() {
    return mySynchonizer.getDocument2();
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myOriginal.getHighlightFile();
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return myOriginal.getOpenFileDescriptor(offset + mySynchonizer.getStartOffset());
  }

  @Nullable
  @Override
  public LineSeparator getLineSeparator() {
    return null;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return null;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myOriginal.getContentType();
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor() {
    return getOpenFileDescriptor(0);
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) {
      if (myAssignments == 0) mySynchonizer.startListen();
      myAssignments++;
    }
    else {
      myAssignments--;
      if (myAssignments == 0) mySynchonizer.stopListen();
    }
    assert myAssignments >= 0;
  }

  private static class MyDocumentsSynchronizer extends DocumentsSynchronizer {
    @NotNull private final RangeMarker myRangeMarker;

    public MyDocumentsSynchronizer(@Nullable Project project,
                                   @NotNull RangeMarker range,
                                   @NotNull Document document1,
                                   @NotNull Document document2) {
      super(project, document1, document2);
      myRangeMarker = range;
    }

    public int getStartOffset() {
      return myRangeMarker.getStartOffset();
    }

    public int getEndOffset() {
      return myRangeMarker.getEndOffset();
    }

    @Override
    protected void onDocumentChanged1(@NotNull DocumentEvent event) {
      if (!myRangeMarker.isValid()) {
        myDocument2.setReadOnly(false);
        replaceString(myDocument2, 0, myDocument2.getTextLength(), "Invalid selection range");
        myDocument2.setReadOnly(true);
        return;
      }
      CharSequence newText = myDocument1.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
      replaceString(myDocument2, 0, myDocument2.getTextLength(), newText);
    }

    @Override
    protected void onDocumentChanged2(@NotNull DocumentEvent event) {
      if (!myRangeMarker.isValid()) {
        return;
      }
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
        replaceString(myDocument2, 0, myDocument2.getTextLength(), "Invalid selection range");
        myDocument2.setReadOnly(true);
      }
      super.startListen();
    }
  }
}
