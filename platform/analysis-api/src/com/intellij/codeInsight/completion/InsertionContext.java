// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains useful information about the current completion session.
 * <p>
 * The following information is available:
 * <ul>
 *   <li>char which was typed to finish completion</li>
 *   <li>list of lookup elements</li>
 *   <li>file which was edited</li>
 *   <li>editor which was edited</li>
 *   <li>offset map which tracks offsets of inserted elements</li>
 *   <li>runnable which should be executed after completing write action is finished. You can update it.</li>
 * </ul>
 * <p>
 * Offset map contains the following offsets by default:
 * <ul>
 *   <li>{@link CompletionInitializationContext#START_OFFSET}</li>
 *   <li>{@link CompletionInitializationContext#SELECTION_END_OFFSET}</li>
 *   <li>{@link CompletionInitializationContext#IDENTIFIER_END_OFFSET}</li>
 * </ul>
 */
@ApiStatus.NonExtendable
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
                          @NotNull LookupElement @NotNull [] elements,
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

  public void setTailOffset(int offset) {
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

  public int getOffset(@NotNull OffsetKey key) {
    return getOffsetMap().getOffset(key);
  }

  public @NotNull OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public @NotNull OffsetKey trackOffset(int offset, boolean movableToRight) {
    OffsetKey key = OffsetKey.create("tracked", movableToRight);
    getOffsetMap().addOffset(key, offset);
    return key;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.START_OFFSET);
  }

  public char getCompletionChar() {
    return myCompletionChar;
  }

  public @NotNull LookupElement @NotNull [] getElements() {
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

  /**
   * See doc of {@link LookupElement#handleInsert}
   */
  public void setLaterRunnable(@Nullable Runnable laterRunnable) {
    myLaterRunnable = laterRunnable;
  }

  /**
   * @param addCompletionChar Whether completionChar should be added to document at tail offset (see {@link #TAIL_OFFSET}) after insert handler (default: {@code true}).
   */
  public void setAddCompletionChar(boolean addCompletionChar) {
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

  /**
   * Creates a new instance of {@link InsertionContext} with the new copy of {@link OffsetMap}
   */
  public @NotNull InsertionContext forkByOffsetMap() {
    return new InsertionContext(myOffsetMap.copyOffsets(myEditor.getDocument()), myCompletionChar, myElements, myPsiFile, myEditor,
                                myAddCompletionChar);
  }
}
