/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class InsertionContext {
  public static final OffsetKey TAIL_OFFSET = OffsetKey.create("tailOffset", true);

  private final OffsetMap myOffsetMap;
  private final char myCompletionChar;
  private final boolean mySignatureSelected;
  private final LookupElement[] myElements;
  private final PsiFile myFile;
  private final Editor myEditor;
  private Runnable myLaterRunnable;
  private boolean myAddCompletionChar = true;

  public InsertionContext(final OffsetMap offsetMap, final char completionChar, final boolean signatureSelected, final LookupElement[] elements,
                          final PsiFile file,
                          final Editor editor) {
    myOffsetMap = offsetMap;
    myCompletionChar = completionChar;
    mySignatureSelected = signatureSelected;
    myElements = elements;
    myFile = file;
    myEditor = editor;
    setTailOffset(editor.getCaretModel().getOffset());
  }

  public void setTailOffset(final int offset) {
    myOffsetMap.addOffset(TAIL_OFFSET, offset);
  }

  public int getTailOffset() {
    return myOffsetMap.getOffset(TAIL_OFFSET);
  }

  public boolean isSignatureSelected() {
    return mySignatureSelected;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
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
   * Whether completionChar should be added to document at tail offset (see {@link #TAIL_OFFSET}) after insert handler.
   * By default this value is true (should be added).
   * @param addCompletionChar
   */
  public void setAddCompletionChar(final boolean addCompletionChar) {
    myAddCompletionChar = addCompletionChar;
  }

  public boolean shouldAddCompletionChar() {
    return myAddCompletionChar;
  }
}
