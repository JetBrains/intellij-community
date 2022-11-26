// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertionContext {
  public static final OffsetKey TAIL_OFFSET = OffsetKey.create("tailOffset", true);

  private final OffsetMap myOffsetMap;
  private final char myCompletionChar;
  private final LookupElement[] myElements;
  private final PsiFile myFile;
  private final Editor myEditor;
  private Runnable myLaterRunnable;
  private boolean myAddCompletionChar;

  public InsertionContext(final OffsetMap offsetMap, final char completionChar, final LookupElement[] elements,
                          @NotNull final PsiFile file,
                          @NotNull final Editor editor, final boolean addCompletionChar) {
    myOffsetMap = offsetMap;
    myCompletionChar = completionChar;
    myElements = elements;
    myFile = file;
    myEditor = editor;
    setTailOffset(editor.getCaretModel().getOffset());
    myAddCompletionChar = addCompletionChar;
  }

  public void setTailOffset(final int offset) {
    myOffsetMap.addOffset(TAIL_OFFSET, offset);
  }

  public int getTailOffset() {
    return myOffsetMap.getOffset(TAIL_OFFSET);
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public void commitDocument() {
    PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
  }

  @NotNull
  public Document getDocument() {
    return getEditor().getDocument();
  }

  public int getOffset(OffsetKey key) {
    return getOffsetMap().getOffset(key);
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public OffsetKey trackOffset(int offset, boolean movableToRight) {
    final OffsetKey key = OffsetKey.create("tracked", movableToRight);
    getOffsetMap().addOffset(key, offset);
    return key;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.START_OFFSET);
  }

  public char getCompletionChar() {
    return myCompletionChar;
  }

  public LookupElement[] getElements() {
    return myElements;
  }

  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  @Nullable
  public Runnable getLaterRunnable() {
    return myLaterRunnable;
  }

  public void setLaterRunnable(@Nullable final Runnable laterRunnable) {
    myLaterRunnable = laterRunnable;
  }

  /**
   * @param addCompletionChar Whether completionChar should be added to document at tail offset (see {@link #TAIL_OFFSET}) after insert handler (default: {@code true}).
   */
  public void setAddCompletionChar(final boolean addCompletionChar) {
    myAddCompletionChar = addCompletionChar;
  }

  public boolean shouldAddCompletionChar() {
    return myAddCompletionChar;
  }


  public static boolean shouldAddCompletionChar(char completionChar) {
    return completionChar != Lookup.AUTO_INSERT_SELECT_CHAR &&
           completionChar != Lookup.REPLACE_SELECT_CHAR &&
           completionChar != Lookup.NORMAL_SELECT_CHAR;
  }

  public InsertionContext forkByOffsetMap() {
    return new InsertionContext(myOffsetMap.copyOffsets(myEditor.getDocument()), myCompletionChar, myElements, myFile, myEditor, myAddCompletionChar);
  }
}
