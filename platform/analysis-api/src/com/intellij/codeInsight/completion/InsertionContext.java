// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final PsiFile myPsiFile;
  private final Editor myEditor;
  private Runnable myLaterRunnable;
  private boolean myAddCompletionChar;

  public InsertionContext(@NotNull OffsetMap offsetMap,
                          char completionChar,
                          LookupElement @NotNull [] elements,
                          @NotNull PsiFile psiFile,
                          @NotNull Editor editor,
                          boolean addCompletionChar) {
    myOffsetMap = offsetMap;
    myCompletionChar = completionChar;
    myElements = elements;
    myPsiFile = psiFile;
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

  public @NotNull PsiFile getFile() {
    return myPsiFile;
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public void commitDocument() {
    PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
  }

  public @NotNull Document getDocument() {
    return getEditor().getDocument();
  }

  public int getOffset(OffsetKey key) {
    return getOffsetMap().getOffset(key);
  }

  public @NotNull OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public @NotNull OffsetKey trackOffset(int offset, boolean movableToRight) {
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

  public @NotNull LookupElement[] getElements() {
    return myElements;
  }

  public @NotNull Project getProject() {
    return myPsiFile.getProject();
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  public @Nullable Runnable getLaterRunnable() {
    return myLaterRunnable;
  }

  public void setLaterRunnable(final @Nullable Runnable laterRunnable) {
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

  public @NotNull InsertionContext forkByOffsetMap() {
    return new InsertionContext(myOffsetMap.copyOffsets(myEditor.getDocument()), myCompletionChar, myElements, myPsiFile, myEditor, myAddCompletionChar);
  }
}
