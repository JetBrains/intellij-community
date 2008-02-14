/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CompletionInitializationContext {
  public static final OffsetKey START_OFFSET = OffsetKey.create("startOffset");
  public static final OffsetKey SELECTION_END_OFFSET = OffsetKey.create("selectionEnd");
  public static final OffsetKey IDENTIFIER_END_OFFSET = OffsetKey.create("identifierEnd");

  public static @NonNls String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  private final Editor myEditor;
  private final PsiFile myFile;
  private final CompletionType myCompletionType;
  private final OffsetMap myOffsetMap;
  private String myDummyIdentifier = DUMMY_IDENTIFIER;

  public CompletionInitializationContext(final Editor editor, final PsiFile file, final CompletionType completionType) {
    myEditor = editor;
    myFile = file;
    myCompletionType = completionType;
    myOffsetMap = new OffsetMap(editor.getDocument());

    final int caretOffset = editor.getCaretModel().getOffset();
    final SelectionModel selectionModel = editor.getSelectionModel();
    myOffsetMap.addOffset(START_OFFSET, selectionModel.hasSelection() ? selectionModel.getSelectionStart() : caretOffset, false);
    
    final int selectionEndOffset = selectionModel.hasSelection() ? selectionModel.getSelectionEnd() : caretOffset;
    myOffsetMap.addOffset(SELECTION_END_OFFSET, selectionEndOffset, true);

    final PsiReference reference = file.findReferenceAt(selectionEndOffset);
    if(reference != null){
      myOffsetMap.addOffset(IDENTIFIER_END_OFFSET,
                            reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset(), true);
    } else {
      myOffsetMap.addOffset(IDENTIFIER_END_OFFSET, selectionEndOffset, true);
    }
  }

  public void setDummyIdentifier(@NotNull final String dummyIdentifier) {
    myDummyIdentifier = dummyIdentifier;
  }

  @NotNull
  public String getDummyIdentifier() {
    return myDummyIdentifier;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public CompletionType getCompletionType() {
    return myCompletionType;
  }

  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(START_OFFSET);
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(SELECTION_END_OFFSET);
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(IDENTIFIER_END_OFFSET);
  }

}
