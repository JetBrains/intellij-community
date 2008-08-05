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
  private final OffsetMap myOffsetMap;
  private final char myCompletionChar;
  private final boolean mySignatureSelected;
  private final LookupElement[] myElements;
  private final PsiFile file;
  private final Editor editor;
  private Runnable myLaterRunnable;

  public InsertionContext(final OffsetMap offsetMap, final char completionChar, final boolean signatureSelected, final LookupElement[] elements,
                          final PsiFile file,
                          final Editor editor) {
    myOffsetMap = offsetMap;
    myCompletionChar = completionChar;
    mySignatureSelected = signatureSelected;
    myElements = elements;
    this.file = file;
    this.editor = editor;
  }

  public boolean isSignatureSelected() {
    return mySignatureSelected;
  }

  public PsiFile getFile() {
    return file;
  }

  public Editor getEditor() {
    return editor;
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
    return file.getProject();
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
}
