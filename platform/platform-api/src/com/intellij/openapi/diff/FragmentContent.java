/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents sub text of other content. Original content should provide not null document.
 */
public class FragmentContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.FragmentContent");
  private final DiffContent myOriginal;
  private final FileType myType;
  private final MyDocumentsSynchronizer mySynchonizer;
  public static final Key<Document> ORIGINAL_DOCUMENT = UndoManager.ORIGINAL_DOCUMENT;
  private final boolean myForceReadOnly;

  public FragmentContent(@NotNull DiffContent original, @NotNull TextRange range, Project project, VirtualFile file) {
    this(original, range, project, file, false);
  }

  public FragmentContent(@NotNull DiffContent original, @NotNull TextRange range, Project project, VirtualFile file, boolean forceReadOnly) {
    this(original, range, project, file != null ? DiffContentUtil.getContentType(file) : null, forceReadOnly);
  }

  public FragmentContent(@NotNull DiffContent original, @NotNull TextRange range, Project project, FileType fileType) {
    this(original, range, project, fileType, false);
  }

  public FragmentContent(@NotNull DiffContent original, @NotNull TextRange range, Project project, FileType fileType, boolean forceReadOnly) {
    RangeMarker rangeMarker = original.getDocument().createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    mySynchonizer = new MyDocumentsSynchronizer(project, rangeMarker);
    myOriginal = original;
    myType = fileType;
    myForceReadOnly = forceReadOnly;
  }

  public FragmentContent(DiffContent original, TextRange range, Project project) {
    this(original, range, project, (FileType)null);
  }

  private static String subText(Document document, int startOffset, int length) {
    return document.getCharsSequence().subSequence(startOffset, startOffset + length).toString();
  }


  @Override
  public void onAssigned(boolean isAssigned) {
    myOriginal.onAssigned(isAssigned);
    mySynchonizer.listenDocuments(isAssigned);
    super.onAssigned(isAssigned);
  }

  @Override
  @NotNull
  public Document getDocument() {
    return mySynchonizer.getCopy();
  }

  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return myOriginal.getOpenFileDescriptor(offset + mySynchonizer.getStartOffset());
  }

  @Override
  public VirtualFile getFile() {
    return null;
  }

  @Override
  @Nullable
  public FileType getContentType() {
    return myType != null ? myType : myOriginal.getContentType();
  }

  @Override
  public byte[] getBytes() throws IOException {
    return getDocument().getText().getBytes();
  }

  public static FragmentContent fromRangeMarker(RangeMarker rangeMarker, Project project) {
    Document document = rangeMarker.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    FileType type = file.getFileType();
    return new FragmentContent(new DocumentContent(project, document), TextRange.create(rangeMarker), project, type);
  }

  private class MyDocumentsSynchronizer extends DocumentsSynchronizer {
    private final RangeMarker myRangeMarker;

    public MyDocumentsSynchronizer(Project project, @NotNull RangeMarker originalRange) {
      super(project);
      myRangeMarker = originalRange;
    }

    public int getStartOffset() {
      return myRangeMarker.getStartOffset();
    }

    @Override
    protected void onOriginalChanged(@NotNull DocumentEvent event, @NotNull Document copy) {
      if (!myRangeMarker.isValid()) {
        fireContentInvalid();
        return;
      }
      replaceString(copy, 0, copy.getTextLength(), subText(event.getDocument(), myRangeMarker.getStartOffset(), getLength()));
    }

    @Override
    protected void beforeListenersAttached(@NotNull Document original, @NotNull Document copy) {
      boolean readOnly = !copy.isWritable();
      if (readOnly) {
        copy.setReadOnly(false);
      }
      replaceString(copy, 0, copy.getTextLength(), subText(original, myRangeMarker.getStartOffset(), getLength()));
      copy.setReadOnly(readOnly);
    }

    private int getLength() {
      return myRangeMarker.getEndOffset() - myRangeMarker.getStartOffset();
    }

    @Override
    protected Document createOriginal() {
      return myRangeMarker.getDocument();
    }

    @NotNull
    @Override
    protected Document createCopy() {
      final Document originalDocument = myRangeMarker.getDocument();
      String textInRange =
        originalDocument.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset()).toString();
      final Document result = EditorFactory.getInstance().createDocument(textInRange);
      result.setReadOnly(myForceReadOnly || !originalDocument.isWritable());
      result.putUserData(ORIGINAL_DOCUMENT, originalDocument);
      return result;
    }

    @Override
    protected void onCopyChanged(@NotNull DocumentEvent event, @NotNull Document original) {
      final int originalOffset = event.getOffset() + myRangeMarker.getStartOffset();
      LOG.assertTrue(originalOffset >= 0);
      if (!original.isWritable()) return;
      final String newText = subText(event.getDocument(), event.getOffset(), event.getNewLength());
      final int originalEnd = originalOffset + event.getOldLength();
      replaceString(original, originalOffset, originalEnd, newText);
    }
  }
}
